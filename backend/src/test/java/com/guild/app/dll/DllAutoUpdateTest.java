package com.guild.app.dll;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.dll.service.DllReleaseService;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Publicacao com version.dll assinado e o manifesto publico que a DLL consulta para se atualizar. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DllAutoUpdateTest {
    static final KeyPair KEYS = generate();

    @DynamicPropertySource
    static void publicKey(DynamicPropertyRegistry registry) {
        registry.add("guild.dll.update-public-key", () -> bcryptBlob((ECPublicKey) KEYS.getPublic()));
    }

    @Autowired DllReleaseService releases;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WebSocketPublisher ws;

    static KeyPair generate() {
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Mesmo formato do dll-signing.ps1 (BCRYPT_ECCPUBLIC_BLOB). */
    static String bcryptBlob(ECPublicKey key) {
        var blob = ByteBuffer.allocate(72).order(ByteOrder.LITTLE_ENDIAN);
        blob.putInt(0x31534345).putInt(32);
        blob.put(fixed(key.getW().getAffineX())).put(fixed(key.getW().getAffineY()));
        return Base64.getEncoder().encodeToString(blob.array());
    }

    static byte[] fixed(BigInteger value) {
        byte[] raw = value.toByteArray(), out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    static String sign(String version, byte[] dll, PrivateKey key) throws GeneralSecurityException {
        var signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(key);
        signer.update(DllSignatureVerifier.signedMessage(version, DllSignatureVerifier.sha256Hex(dll))
                .getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    /** DLL x86 minima com o User-Agent da versao em UTF-16. */
    static byte[] fakeDll(String version) {
        byte[] dll = new byte[0x600];
        dll[0] = 'M'; dll[1] = 'Z';
        ByteBuffer.wrap(dll).order(ByteOrder.LITTLE_ENDIAN).putInt(0x3C, 0x80)
                .put(0x80, (byte) 'P').put(0x81, (byte) 'E').putShort(0x84, (short) 0x14C).putShort(0x96, (short) 0x2102);
        byte[] ua = ("CerberusBags/" + version + "\0").getBytes(StandardCharsets.UTF_16LE);
        System.arraycopy(ua, 0, dll, 0x200, ua.length);
        return dll;
    }

    static MockMultipartFile dll(byte[] bytes) {
        return new MockMultipartFile("dll", "version.dll", "application/octet-stream", bytes);
    }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM dll_release");
        long admin = jdbc.queryForObject(
                "INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'ADMINISTRADOR','APROVADO',true) RETURNING id",
                Long.class, "Upd" + UUID.randomUUID().toString().substring(0, 8));
        var principal = new MemberPrincipal(members.findById(admin).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void signedDllBecomesTheUpdateAndGetsAZipForManualDownload() throws Exception {
        byte[] bytes = fakeDll("1.2.0");
        var view = releases.publish("1.2.0", "Local", null, dll(bytes), sign("1.2.0", bytes, KEYS.getPrivate()));
        assertTrue(view.autoUpdate());
        assertEquals("CerberusBags-1.2.0.zip", view.fileName());

        var manifest = releases.updateManifest().orElseThrow();
        assertEquals("1.2.0", manifest.version());
        assertEquals(DllSignatureVerifier.sha256Hex(bytes), manifest.sha256());
        assertEquals(bytes.length, manifest.size());
        assertArrayEquals(bytes, releases.updateFile("1.2.0").orElseThrow());

        try (var zip = new ZipInputStream(new ByteArrayInputStream(releases.latestFile().orElseThrow().getFileData()))) {
            assertEquals("version.dll", zip.getNextEntry().getName());
            assertArrayEquals(bytes, zip.readAllBytes());
        }
    }

    @Test void wrongSignatureVersionOrFileIsRefused() throws Exception {
        byte[] bytes = fakeDll("1.2.0");
        var otherKey = generate().getPrivate();
        assertThrows(AppException.class, () ->
                releases.publish("1.2.0", null, null, dll(bytes), sign("1.2.0", bytes, otherKey)));      // outra chave
        assertThrows(AppException.class, () ->
                releases.publish("1.2.0", null, null, dll(bytes), sign("1.2.1", bytes, KEYS.getPrivate()))); // assinou outra versao
        assertThrows(AppException.class, () ->
                releases.publish("1.2.1", null, null, dll(bytes), sign("1.2.1", bytes, KEYS.getPrivate()))); // DLL e da 1.2.0
        assertThrows(AppException.class, () ->
                releases.publish("1.2.0", null, null, dll(bytes), null));                                  // sem assinatura
        byte[] notDll = bytes.clone(); notDll[0] = 'X';
        assertThrows(AppException.class, () ->
                releases.publish("1.2.0", null, null, dll(notDll), sign("1.2.0", notDll, KEYS.getPrivate())));
        assertTrue(releases.updateManifest().isEmpty());
    }

    @Test void packageOnlyReleaseHasNoAutoUpdate() throws Exception {
        var view = releases.publish("1.1.0", null,
                new MockMultipartFile("file", "version.rar", "application/octet-stream", new byte[] {1, 2, 3}));
        assertFalse(view.autoUpdate());
        assertTrue(releases.updateManifest().isEmpty());
        assertTrue(releases.updateFile("1.1.0").isEmpty());
    }

    @Test void productionKeyIsAValidP256Key() {
        // A chave de application.yml (a mesma embutida na DLL) tem que carregar.
        assertDoesNotThrow(() -> new DllSignatureVerifier(
                "RUNTMSAAAABCGs/WyFdkZjh4ft6MINXu+OodDRmMmqzcPMAQ0bzP5VFdONzSbYrH+lf4llhmJDtLH/3ZCIfBK9tU6jjnu/uu"));
    }
}
