package com.guild.app.dll;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Confere a assinatura das atualizacoes automaticas da DLL com a mesma chave publica que a
 * DLL traz embutida (guild.dll.update-public-key). Assim o site nunca serve um arquivo que a
 * DLL recusaria, e um erro de publicacao aparece na hora, nao no PC dos membros.
 */
@Component
public class DllSignatureVerifier {

    /** BCRYPT_ECDSA_PUBLIC_P256_MAGIC ("ECS1"). */
    private static final int P256_PUBLIC_MAGIC = 0x31534345;

    private final PublicKey publicKey;

    public DllSignatureVerifier(@Value("${guild.dll.update-public-key}") String publicBlobBase64) {
        this.publicKey = fromBcryptBlob(Base64.getDecoder().decode(publicBlobBase64.trim()));
    }

    public static String signedMessage(String version, String sha256) {
        return "CerberusBags-update\n" + version + "\n" + sha256;
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Assinatura r||s (64 bytes, base64) sobre a mensagem da versao e do sha256. */
    public boolean verify(String version, String sha256, String signatureBase64) {
        try {
            byte[] signature = Base64.getDecoder().decode(signatureBase64.trim());
            if (signature.length != 64) return false;
            Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
            verifier.initVerify(publicKey);
            verifier.update(signedMessage(version, sha256).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }

    /** BCRYPT_ECCPUBLIC_BLOB: magic, tamanho da coordenada (32) e X, Y big-endian. */
    static PublicKey fromBcryptBlob(byte[] blob) {
        ByteBuffer header = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        if (blob.length != 72 || header.getInt() != P256_PUBLIC_MAGIC || header.getInt() != 32) {
            throw new IllegalArgumentException("guild.dll.update-public-key nao e uma chave publica ECDSA P-256");
        }
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(blob, 8, 40)),
                    new BigInteger(1, Arrays.copyOfRange(blob, 40, 72)));
            return KeyFactory.getInstance("EC").generatePublic(
                    new ECPublicKeySpec(point, parameters.getParameterSpec(ECParameterSpec.class)));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("guild.dll.update-public-key invalida", e);
        }
    }
}
