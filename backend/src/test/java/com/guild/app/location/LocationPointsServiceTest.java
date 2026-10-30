package com.guild.app.location;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.characterstatus.dto.CharacterStatusIngestRequest;
import com.guild.app.characterstatus.service.CharacterStatusService;
import com.guild.app.common.exception.AppException;
import com.guild.app.location.dto.LocationPointsDtos.*;
import com.guild.app.location.service.LocationPointsService;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Pontos por local: previa por mapa/regiao/fila e confirmacao so para quem ainda esta la. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocationPointsServiceTest {
    @Autowired LocationPointsService service;
    @Autowired CharacterStatusService statusService;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean WebSocketPublisher ws;

    String tag;
    long moderator, inQueue, notInQueue, elsewhere;
    long gid = 1000;

    long member(String role) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id",
                Long.class, tag + (gid++), role);
    }
    String nick(long id) { return members.findById(id).orElseThrow().getNickname(); }
    void at(long id, String map, String region) {
        statusService.ingest(new CharacterStatusIngestRequest(gid++, nick(id), 50, 1f, 10, 10, 1, 1, 1, 1,
                map, region, 54, 68), "1.1.0");
    }
    void loginAs(long id) {
        var p = new MemberPrincipal(members.findById(id).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
    Set<Long> ids(List<LocationMatch> matches) {
        var out = new HashSet<Long>();
        matches.forEach(m -> out.add(m.memberId()));
        return out;
    }
    long points(long id) { em.flush(); return jdbc.queryForObject("SELECT points FROM member WHERE id=?", Long.class, id); }
    int priority(long id) { em.flush(); return jdbc.queryForObject("SELECT party_priority_points FROM member WHERE id=?", Integer.class, id); }

    @BeforeEach void setup() {
        tag = "Loc" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        moderator = member("MODERADOR");
        inQueue = member("MEMBRO");
        notInQueue = member("MEMBRO");
        elsewhere = member("MEMBRO");
        jdbc.update("INSERT INTO party_lfg(member_id,created_at) VALUES(?,now())", inQueue);
        at(inQueue, "Coldron Volcanic Area", "Vapor Lake");
        at(notInQueue, "Coldron Volcanic Area", "vapor lake");
        at(elsewhere, "Coldron Volcanic Area", "Evil Horn");
        loginAs(moderator);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void previewFiltersByRegionMapAndQueue() {
        var all = ids(service.preview(new PreviewRequest(null, "Vapor Lake", Scope.ALL)));
        assertTrue(all.containsAll(List.of(inQueue, notInQueue)));
        assertFalse(all.contains(elsewhere));

        var queue = ids(service.preview(new PreviewRequest("Coldron Volcanic Area", "Vapor Lake", Scope.QUEUE)));
        assertTrue(queue.contains(inQueue));
        assertFalse(queue.contains(notInQueue));

        var wholeMap = ids(service.preview(new PreviewRequest("coldron volcanic area", null, Scope.ALL)));
        assertTrue(wholeMap.containsAll(List.of(inQueue, notInQueue, elsewhere)));
    }

    @Test void needsMapOrRegion() {
        assertThrows(AppException.class, () -> service.preview(new PreviewRequest(" ", null, Scope.ALL)));
    }

    @Test void generalPointsOnlyForWhoIsStillThere() {
        at(notInQueue, "Coldron Volcanic Area", "Evil Horn"); // saiu entre a previa e a confirmacao
        var result = service.award(new AwardRequest(null, "Vapor Lake", Scope.ALL, PointsType.GENERAL, 5,
                List.of(inQueue, notInQueue), null, null), new MemberPrincipal(members.findById(moderator).orElseThrow()));

        assertEquals(1, result.awarded());
        assertEquals(List.of(nick(notInQueue)), result.skipped());
        assertEquals(5, points(inQueue));
        assertEquals(0, points(notInQueue));
    }

    @Test void priorityPointsGoToTheQueue() {
        service.award(new AwardRequest(null, "Vapor Lake", Scope.QUEUE, PointsType.PRIORITY, 2,
                List.of(inQueue), "Boss", "Coldron"), new MemberPrincipal(members.findById(moderator).orElseThrow()));
        assertEquals(2, priority(inQueue));
        assertEquals(0, points(inQueue));
    }

    @Test void statusWithoutLocationClearsIt() {
        statusService.ingest(new CharacterStatusIngestRequest(gid++, nick(elsewhere), 50, 1f, 10, 10, 1, 1, 1, 1), "1.0.0");
        // O envio sem local e de outro personagem (gid novo): o antigo ainda conta.
        assertTrue(ids(service.preview(new PreviewRequest(null, "Evil Horn", Scope.ALL))).contains(elsewhere));
        Long oldGid = jdbc.queryForObject("SELECT gid FROM character_status WHERE member_id=? AND map_name IS NOT NULL", Long.class, elsewhere);
        statusService.ingest(new CharacterStatusIngestRequest(oldGid, nick(elsewhere), 50, 1f, 10, 10, 1, 1, 1, 1), "1.0.0");
        assertFalse(ids(service.preview(new PreviewRequest(null, "Evil Horn", Scope.ALL))).contains(elsewhere));
    }

    @Test void knownRegionsStayListedAfterEveryoneLeaves() {
        String region = tag + " Cave";
        at(elsewhere, "Coldron Volcanic Area", region);
        at(elsewhere, "Coldron Volcanic Area", "Evil Horn");
        var option = service.locations().stream()
                .filter(o -> region.equals(o.region()))
                .findFirst().orElseThrow();
        assertEquals("Coldron Volcanic Area", option.map());
        assertEquals(0, option.members());
    }

    @Test void membersCannotUseIt() {
        loginAs(notInQueue);
        assertThrows(AppException.class, () -> service.locations());
    }
}
