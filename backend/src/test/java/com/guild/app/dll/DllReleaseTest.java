package com.guild.app.dll;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.characterstatus.dto.CharacterStatusIngestRequest;
import com.guild.app.characterstatus.service.CharacterStatusService;
import com.guild.app.characterstatus.service.SyncAccessService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DllReleaseTest {
    @Autowired DllReleaseService releases;
    @Autowired CharacterStatusService statuses;
    @Autowired SyncAccessService syncAccess;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WebSocketPublisher ws;
    long adminId, memberId;
    String nick;

    long member(String nickname, String role) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id",
            Long.class, nickname, role);
    }
    void authenticate(long id) {
        var principal = new MemberPrincipal(members.findById(id).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    MockMultipartFile rar() { return new MockMultipartFile("file", "version.rar", "application/octet-stream", new byte[] {1, 2, 3}); }
    void sync(String dllVersion) {
        statuses.ingest(new CharacterStatusIngestRequest(9L, nick, 50, 1f, 10, 10, 1, 1, 1, 1), dllVersion);
    }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM dll_release");
        nick = "Dll" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        adminId = member("Adm" + nick, "ADMINISTRADOR");
        memberId = member(nick, "MEMBRO");
        authenticate(adminId);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void versionParsingAndOrdering() {
        assertEquals("1.1.0", DllVersion.fromUserAgent("CerberusBags/1.1.0"));
        assertEquals("0.1", DllVersion.fromUserAgent("CerberusBags/0.1"));
        assertNull(DllVersion.fromUserAgent("curl/8.0"));
        assertTrue(DllVersion.compare("1.10", "1.9") > 0);
        assertEquals(0, DllVersion.compare("1.0", "1.0.0"));
    }

    @Test void publishingRequiresAHigherVersionAndNotifiesEveryone() throws Exception {
        var first = releases.publish("1.1.0", "Primeira", rar());
        verify(ws).publishDllRelease(any());
        assertEquals("1.1.0", releases.latest().orElseThrow().version());
        assertArrayEquals(new byte[] {1, 2, 3}, releases.latestFile().orElseThrow().getFileData());
        assertEquals(first.id(), releases.releases().getFirst().id());

        assertThrows(AppException.class, () -> releases.publish("1.0.9", null, rar()));
        assertThrows(AppException.class, () -> releases.publish("1.1.0", null, rar()));
        assertThrows(AppException.class, () -> releases.publish("abc", null, rar()));
        assertThrows(AppException.class, () -> releases.publish("1.2.0", null,
            new MockMultipartFile("file", "version.dll", "application/octet-stream", new byte[] {1})));
    }

    @Test void onlyAdministratorsPublish() {
        authenticate(memberId);
        assertThrows(AppException.class, () -> releases.publish("1.1.0", null, rar()));
    }

    @Test void memberOnAnOlderDllIsOutdatedButStillSynced() throws Exception {
        releases.publish("1.1.0", null, rar());
        sync("0.1");
        var status = syncAccess.status(memberId, Instant.now());
        assertTrue(status.synced());
        assertTrue(status.dllOutdated());
        assertEquals("0.1", status.dllVersion());
        assertEquals("1.1.0", status.latestDllVersion());
        assertTrue(releases.members().stream().anyMatch(m -> m.memberId() == memberId && m.outdated()));

        sync("1.1.0");
        assertFalse(syncAccess.status(memberId, Instant.now()).dllOutdated());
    }
}
