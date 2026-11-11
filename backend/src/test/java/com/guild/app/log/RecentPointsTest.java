package com.guild.app.log;

import com.guild.app.log.dto.RecentPointsEntry;
import com.guild.app.log.service.LogService;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Dashboard: últimas rodadas de pontos, uma linha por evento / rodada de RG, sem jogador. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecentPointsTest {
    @Autowired LogService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WebSocketPublisher ws;
    @Autowired com.guild.app.member.repository.MemberRepository members;
    @Autowired com.guild.app.event.service.EventService events;
    @Autowired jakarta.persistence.EntityManager em;

    String tag;
    long a, b, c, objective;

    long member() {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'MEMBRO','APROVADO',true) RETURNING id",
                Long.class, tag + UUID.randomUUID().toString().substring(0, 4));
    }

    /** Log com created_at = agora menos {minutesAgo} minutos. */
    void log(String type, long target, int minutesAgo, String payload) {
        jdbc.update("INSERT INTO audit_log(type,actor_id,target_id,payload,created_at) VALUES(?,?,?,?::jsonb,now() - make_interval(mins => ?))",
                type, target, target, payload, minutesAgo);
    }

    long claim(long member, boolean denied) {
        return jdbc.queryForObject("INSERT INTO event_claim(member_id,objective_id,points,denied) VALUES(?,?,5,?) RETURNING id",
                Long.class, member, objective, denied);
    }

    List<RecentPointsEntry> mine() { return mine(a); }

    List<RecentPointsEntry> mine(long viewer) {
        return service.recentPoints(50, viewer).stream()
                .filter(e -> e.name() != null && e.name().startsWith(tag) || tag.equals(e.detail()))
                .toList();
    }

    @BeforeEach void setup() {
        tag = "Rp" + UUID.randomUUID().toString().substring(0, 6);
        a = member(); b = member(); c = member();
        objective = jdbc.queryForObject("INSERT INTO objective(name,points,created_by_id) VALUES(?,5,?) RETURNING id",
                Long.class, tag + " Boss", a);
    }

    @Test void oneLinePerEventWithoutDeniedClaims() {
        String boss = "\"objectiveName\":\"" + tag + " Boss\",\"points\":5,\"eventId\":991";
        log("EVENT_CLAIMED", a, 40, "{" + boss + ",\"claimId\":" + claim(a, false) + "}");
        log("EVENT_CLAIMED", b, 5, "{" + boss + ",\"claimId\":" + claim(b, false) + "}");
        // removido pelo admin: não conta
        log("EVENT_CLAIMED", c, 4, "{" + boss + ",\"claimId\":" + claim(c, true) + "}");

        var rows = mine();
        assertEquals(1, rows.size(), "o mesmo evento junta mesmo com 35 min entre os resgates");
        var row = rows.get(0);
        assertEquals("EVENT", row.kind());
        assertEquals(2, row.members());
        assertEquals(0, new BigDecimal(5).compareTo(row.points()));
        assertEquals(0, new BigDecimal(10).compareTo(row.total()));
    }

    @Test void claimsWithoutEventGroupByTenMinuteGaps() {
        String cw = "{\"objectiveName\":\"" + tag + " CW\",\"points\":3,\"source\":\"CW\"}";
        log("EVENT_CLAIMED", a, 62, cw);
        log("EVENT_CLAIMED", b, 60, cw); // mesma rodada (2 min)
        log("EVENT_CLAIMED", c, 2, cw);  // outra CW, uma hora depois

        var rows = mine();
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).members(), "mais recente primeiro");
        assertEquals(2, rows.get(1).members());
    }

    @Test void rgByPlaceAndProximityWithoutNames() {
        String rg = "{\"amount\":2,\"objectiveName\":\"RG\",\"source\":\"RG\",\"reason\":\"Pontos de RG: " + tag + "\"}";
        log("POINTS_ADJUSTED", a, 3, rg);
        log("POINTS_ADJUSTED", b, 3, rg);
        log("POINTS_ADJUSTED", a, 1, "{\"amount\":1,\"source\":\"PROXIMITY\",\"reason\":\"" + tag + " Spot (perto de Alice, Bob)\"}");
        // ajuste manual de admin (sem source) fica de fora
        log("POINTS_ADJUSTED", c, 0, "{\"amount\":50,\"reason\":\"" + tag + " manual\"}");

        var rows = mine();
        assertEquals(2, rows.size());
        assertEquals("PROXIMITY", rows.get(0).kind());
        assertEquals(tag + " Spot", rows.get(0).name(), "sem a lista de quem estava perto");
        assertEquals("RG", rows.get(1).kind());
        assertEquals(tag, rows.get(1).detail());
        assertEquals(2, rows.get(1).members());
    }

    @Test void marksWhatTheViewerReceived() {
        String boss = "\"objectiveName\":\"" + tag + " Boss\",\"points\":5,\"eventId\":992";
        log("EVENT_CLAIMED", a, 3, "{" + boss + ",\"claimId\":" + claim(a, false) + "}");
        log("EVENT_CLAIMED", b, 2, "{" + boss + ",\"claimId\":" + claim(b, false) + "}");

        var forA = mine(a).get(0);
        assertTrue(forA.received());
        assertEquals(0, new BigDecimal(5).compareTo(forA.myPoints()));
        assertFalse(forA.limited());

        var forC = mine(c).get(0);
        assertFalse(forC.received());
        assertNull(forC.myPoints());
        assertFalse(forC.limited(), "objetivo normal: só não recebeu");
    }

    @Test void limitedEventWhenViewerHadTheDailyCap() {
        jdbc.update("UPDATE objective SET type='LIMITADO', daily_limit=1 WHERE id=?", objective);
        // c já tinha resgatado o objetivo hoje, antes desta rodada
        jdbc.update("INSERT INTO event_claim(member_id,objective_id,points,claimed_at) VALUES(?,?,5,now() - interval '1 minute')", c, objective);
        String boss = "\"objectiveName\":\"" + tag + " Boss\",\"points\":5,\"eventId\":993";
        log("EVENT_CLAIMED", a, 0, "{" + boss + ",\"claimId\":" + claim(a, false) + "}");

        assertTrue(mine(c).get(0).limited(), "c estava no limite");
        assertFalse(mine(b).get(0).limited(), "b não tinha resgatado: só não recebeu");
    }

    @Test void limitedRgWhenViewerHadTwoThatCwDay() {
        jdbc.update("INSERT INTO rg_award(member_id,day,cw_slot,points,at) VALUES(?,current_date,1,2,now() - interval '2 hours'),(?,current_date,1,2,now() - interval '1 hour')", c, c);
        jdbc.update("INSERT INTO rg_award(member_id,day,cw_slot,points,at) VALUES(?,current_date,2,2,now())", a);
        log("POINTS_ADJUSTED", a, 0, "{\"amount\":2,\"objectiveName\":\"RG\",\"source\":\"RG\",\"reason\":\"Pontos de RG: " + tag + "\"}");

        assertTrue(mine(a).get(0).received());
        assertTrue(mine(c).get(0).limited(), "c já tinha 2 RG no dia");
        assertFalse(mine(b).get(0).limited());
    }

    @Test void adminCancelsARoundAndEveryoneLosesThePoints() {
        long admin = jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'ADMINISTRADOR','APROVADO',true) RETURNING id",
                Long.class, tag + "adm");
        jdbc.update("UPDATE member SET points=20 WHERE id IN (?,?)", a, b);
        String boss = "\"objectiveName\":\"" + tag + " Boss\",\"points\":5";
        long ca = claim(a, false), cb = claim(b, false);
        log("EVENT_CLAIMED", a, 3, "{" + boss + ",\"claimId\":" + ca + "}");
        log("EVENT_CLAIMED", b, 2, "{" + boss + ",\"claimId\":" + cb + "}");

        assertNull(mine().get(0).claimIds(), "membro não vê os resgates");
        var row = service.recentPoints(50, admin, true).stream().filter(e -> e.name().startsWith(tag)).findFirst().orElseThrow();
        assertEquals(List.of(ca, cb), row.claimIds());

        var principal = new com.guild.app.auth.security.MemberPrincipal(members.findById(admin).orElseThrow());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            assertEquals(2, events.denyClaims(new com.guild.app.event.dto.DenyClaimsRequest(row.claimIds(), "lançado em dobro"), principal));
            assertEquals(0, events.denyClaims(new com.guild.app.event.dto.DenyClaimsRequest(row.claimIds(), "de novo"), principal));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        em.flush();
        assertEquals(15L, jdbc.queryForObject("SELECT points FROM member WHERE id=?", Long.class, a));
        assertEquals(15L, jdbc.queryForObject("SELECT points FROM member WHERE id=?", Long.class, b));
        assertTrue(mine().isEmpty(), "rodada cancelada some do painel");
    }
}
