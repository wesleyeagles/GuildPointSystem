package com.guild.app.raid;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.raid.service.RaidLiveService;
import com.guild.app.raid.service.RaidLiveService.LiveMember;
import com.guild.app.raid.service.RaidService;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Sem @Transactional: a escalação em cache só recarrega depois do commit, como em produção. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RaidLiveTest {
    @Autowired RaidService raids;
    @Autowired RaidLiveService live;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean WebSocketPublisher ws;
    final List<Long> created = new ArrayList<>();
    long leaderId, otherId, outsiderId;
    String leaderNick, otherNick, outsiderNick;

    long member(String nick) {
        long id = jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'MEMBRO','APROVADO',true) RETURNING id",
            Long.class, nick);
        created.add(id);
        return id;
    }
    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    MemberPrincipal as(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }

    @BeforeEach void setup() {
        leaderNick = nick("Lead"); otherNick = nick("Heal"); outsiderNick = nick("Out");
        leaderId = member(leaderNick); otherId = member(otherNick); outsiderId = member(outsiderNick);
    }
    @AfterEach void cleanup() {
        for (long id : created) {
            jdbc.update("DELETE FROM raid WHERE leader_id=?", id);
            jdbc.update("DELETE FROM audit_log WHERE actor_id=? OR target_id=?", id, id);
            jdbc.update("DELETE FROM member WHERE id=?", id);
        }
    }

    String report(String character, String body) throws Exception {
        return mvc.perform(post("/api/raids/game/status").param("character", character)
                .contentType(MediaType.TEXT_PLAIN).content(body))
            .andReturn().getResponse().getContentAsString();
    }

    @Test void memberOutsideARaidGetsNoContent() throws Exception {
        mvc.perform(post("/api/raids/game/status").param("character", outsiderNick)
                .contentType(MediaType.TEXT_PLAIN).content("50\t100\t200\t10\t20\t5\t10"))
            .andExpect(status().isNoContent());
    }

    @Test void reportReturnsTheWholeRaidWithEachMembersVitals() throws Exception {
        var raid = raids.create("PB 21h", as(leaderId));
        raids.addMembers(raid.id(), List.of(otherId), 2, false, as(leaderId));

        String text = report(leaderNick.toLowerCase(), "55\t900\t1000\t300\t400\t50\t60\n");
        var lines = text.split("\n");
        assertEquals("R\t" + raid.id() + "\tPB 21h", lines[0]);
        assertEquals(3, lines.length);
        var me = lines[1].split("\t");
        assertEquals(List.of("1", leaderNick, "55", "900", "1000", "300", "400", "50", "60", "LP", "0"), List.of(me));
        var other = lines[2].split("\t");
        assertEquals("2", other[0]);
        assertEquals(otherNick, other[1]);
        assertEquals("P", other[9]);
        assertEquals("-1", other[10]); // ainda sem envio

        report(otherNick, "50\t10\t800\t0\t300\t0\t40");
        List<LiveMember> view = live.forRaid(raid.id(), as(outsiderId));
        var healer = view.stream().filter(m -> m.memberId() == otherId).findFirst().orElseThrow();
        assertEquals(10, healer.hp());
        assertEquals(800, healer.hpMax());
        assertTrue(healer.ageMs() >= 0);
    }

    @Test void rosterChangesAreSeenOnTheNextReport() throws Exception {
        var raid = raids.create("Raid", as(leaderId));
        assertTrue(report(otherNick, "50\t1\t2\t1\t2\t1\t2").isEmpty());
        raids.addMembers(raid.id(), List.of(otherId), null, false, as(leaderId));
        assertTrue(report(otherNick, "50\t1\t2\t1\t2\t1\t2").contains(otherNick));
        raids.removeMember(raid.id(), otherId, as(otherId));
        assertTrue(report(otherNick, "50\t1\t2\t1\t2\t1\t2").isEmpty());
    }

    @Test void broadcastPublishesOnlyRaidsThatReceivedReports() throws Exception {
        var raid = raids.create("Raid", as(leaderId));
        live.broadcast();
        clearInvocations(ws);
        report(leaderNick, "50\t1\t2\t1\t2\t1\t2");
        live.broadcast();
        live.broadcast();
        verify(ws, times(1)).publishRaidLive(eq(raid.id()), argThat(list -> list instanceof List<?> l && l.size() == 1));
    }

    @Test void effectsTravelOnlyToDllsThatAskForThem() throws Exception {
        var raid = raids.create("Raid", as(leaderId));
        raids.addMembers(raid.id(), List.of(otherId), null, false, as(leaderId));
        // Um efeito com ícone (6 campos) e um no formato sem ícone (5 campos), que continua aceito.
        report(otherNick, "50\t10\t800\t0\t300\t0\t40\nE\t0,3,7,B,4,Rage Range;2,45,1,D,Trust");

        String withFx = mvc.perform(post("/api/raids/game/status").param("character", leaderNick).param("fx", "1")
                .contentType(MediaType.TEXT_PLAIN).content("55\t900\t1000\t300\t400\t50\t60\nE\t"))
            .andReturn().getResponse().getContentAsString();
        var lines = withFx.split("\n");
        assertEquals(12, lines[1].split("\t", -1).length);
        assertEquals("", lines[1].split("\t", -1)[11]);  // o lider nao tem efeitos
        assertEquals("0,3,7,B,4,Rage Range;2,45,1,D,-1,Trust", lines[2].split("\t", -1)[11]);

        String old = report(leaderNick, "55\t900\t1000\t300\t400\t50\t60");  // DLL 1.6.0: sem fx
        for (String line : old.split("\n")) if (!line.startsWith("R\t")) assertEquals(11, line.split("\t", -1).length);

        var healer = live.forRaid(raid.id(), as(outsiderId)).stream().filter(m -> m.memberId() == otherId).findFirst().orElseThrow();
        assertEquals(List.of(new RaidLiveService.Effect(0, 3, 7, false, 4, "Rage Range"), new RaidLiveService.Effect(2, 45, 1, true, -1, "Trust")),
            healer.effects());
    }

    @Test void markGoesOnlyToDllsThatAskForIt() throws Exception {
        var raid = raids.create("Raid", as(leaderId));
        raids.setMark(raid.id(), leaderId, 3, as(leaderId));
        String withFx = mvc.perform(post("/api/raids/game/status").param("character", leaderNick).param("fx", "1")
                .contentType(MediaType.TEXT_PLAIN).content("55\t900\t1000\t300\t400\t50\t60"))
            .andReturn().getResponse().getContentAsString();
        assertEquals("LP3", withFx.split("\n")[1].split("\t", -1)[9]);
        String old = report(leaderNick, "55\t900\t1000\t300\t400\t50\t60");
        assertEquals("LP", old.split("\n")[1].split("\t", -1)[9]);
    }

    @Test void hiddenMembersKeepSendingButNeverSeeTheRaid() throws Exception {
        var raid = raids.create("Raid", as(leaderId));
        raids.addMembers(raid.id(), List.of(otherId), null, false, as(leaderId));
        raids.setHidden(raid.id(), otherId, true, as(leaderId));

        // DLL 1.7.0 do oculto: "O" (continua a cada 1 s, sem painel); DLL 1.6.0: 204
        String observed = mvc.perform(post("/api/raids/game/status").param("character", otherNick).param("fx", "1")
                .contentType(MediaType.TEXT_PLAIN).content("50\t10\t800\t0\t300\t0\t40\nE\t0,3,7,B,4,Rage Range"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("O\n", observed);
        mvc.perform(post("/api/raids/game/status").param("character", otherNick)
                .contentType(MediaType.TEXT_PLAIN).content("50\t10\t800\t0\t300\t0\t40"))
            .andExpect(status().isNoContent());

        // o líder (visível) recebe o oculto no painel, com os dados
        String leaderView = report(leaderNick, "55\t900\t1000\t300\t400\t50\t60");
        assertTrue(leaderView.contains(otherNick + "\t50\t10\t800"));

        // no site: quem gerencia vê o oculto; os outros não
        assertTrue(live.forRaid(raid.id(), as(leaderId)).stream().anyMatch(m -> m.memberId() == otherId));
        assertTrue(live.forRaid(raid.id(), as(outsiderId)).stream().noneMatch(m -> m.memberId() == otherId));
        assertTrue(raids.list(as(outsiderId)).stream().filter(r -> r.id().equals(raid.id())).findFirst().orElseThrow()
            .members().stream().noneMatch(m -> m.id() == otherId));
        assertTrue(raids.list(as(otherId)).stream().filter(r -> r.id().equals(raid.id())).findFirst().orElseThrow()
            .members().stream().noneMatch(m -> m.id() == otherId));

        // o canal ao vivo sem os ocultos e o completo
        clearInvocations(ws);
        live.broadcast();
        verify(ws).publishRaidLive(eq(raid.id()), argThat(l -> l instanceof List<?> list && list.size() == 1));
        verify(ws).publishRaidLiveAll(eq(raid.id()), argThat(l -> l instanceof List<?> list && list.size() == 2));
    }

    String reportFx(String character, int fx, String body) throws Exception {
        return mvc.perform(post("/api/raids/game/status").param("character", character).param("fx", String.valueOf(fx))
                .contentType(MediaType.TEXT_PLAIN).content(body))
            .andReturn().getResponse().getContentAsString();
    }

    @Test void flagsAreSharedWithTheRaidAndExpire() throws Exception {
        var raid = raids.create("Raid", as(leaderId));
        raids.addMembers(raid.id(), List.of(otherId), null, false, as(leaderId));
        String vitals = "50\t10\t800\t0\t300\t0\t40\nE\t\nL\t1\t100\t200";
        String placed = reportFx(otherNick, 3, vitals + "\nF\t1\t7920\t-2224");
        assertTrue(placed.endsWith("F\t" + otherNick + "\t1,7920,-2224\t0\n"), placed);
        // O próximo envio sem "F" mantém a bandeira; os outros da raid a recebem.
        String leaderView = reportFx(leaderNick, 3, "55\t900\t1000\t300\t400\t50\t60");
        assertTrue(leaderView.contains("F\t" + otherNick + "\t1,7920,-2224\t"), leaderView);
        // DLL que não pede fx=3 não recebe a linha (a 1.8.0 recusaria a resposta).
        assertFalse(reportFx(leaderNick, 2, "55\t900\t1000\t300\t400\t50\t60").contains("F\t"));
        // Nova bandeira substitui; "F -" tira.
        assertTrue(reportFx(otherNick, 3, vitals + "\nF\t1\t10\t20").contains("\t1,10,20\t"));
        assertFalse(reportFx(otherNick, 3, vitals + "\nF\t-").contains("F\t"));
        // Expira em 2 minutos.
        reportFx(otherNick, 3, vitals + "\nF\t1\t10\t20");
        var clock = RaidLiveService.class.getDeclaredField("clock");
        clock.setAccessible(true);
        clock.set(live, java.time.Clock.offset(java.time.Clock.systemUTC(), java.time.Duration.ofMinutes(3)));
        try {
            assertFalse(reportFx(leaderNick, 3, "55\t900\t1000\t300\t400\t50\t60").contains("F\t"));
        } finally {
            clock.set(live, java.time.Clock.systemUTC());
        }
        for (String bad : List.of("F\t1\t2", "F\tx", "F\t1\t0\t2000000"))
            mvc.perform(post("/api/raids/game/status").param("character", leaderNick)
                    .contentType(MediaType.TEXT_PLAIN).content("55\t900\t1000\t300\t400\t50\t60\n" + bad))
                .andExpect(status().isBadRequest());
    }

    @Test void hiddenMembersCannotPlaceFlags() throws Exception {
        var raid = raids.create("Raid", as(leaderId));
        raids.addMembers(raid.id(), List.of(otherId), null, false, as(leaderId));
        raids.setHidden(raid.id(), otherId, true, as(leaderId));
        assertEquals("O\n", reportFx(otherNick, 3, "50\t10\t800\t0\t300\t0\t40\nF\t1\t10\t20"));
        assertFalse(reportFx(leaderNick, 3, "55\t900\t1000\t300\t400\t50\t60").contains("F\t"));
    }

    @Test void positionsGoOnlyToDllsThatAskForThem() throws Exception {
        var raid = raids.create("Raid", as(leaderId));
        raids.addMembers(raid.id(), List.of(otherId), null, false, as(leaderId));
        report(otherNick, "50\t10\t800\t0\t300\t0\t40\nE\t\nL\t1\t7920\t-2224");
        String fx2 = mvc.perform(post("/api/raids/game/status").param("character", leaderNick).param("fx", "2")
                .contentType(MediaType.TEXT_PLAIN).content("55\t900\t1000\t300\t400\t50\t60\nE\t\nL\t1\t100\t200"))
            .andReturn().getResponse().getContentAsString();
        var lines = fx2.split("\n");
        assertEquals("1,100,200", lines[1].split("\t", -1)[12]);
        assertEquals("1,7920,-2224", lines[2].split("\t", -1)[12]);
        String fx1 = mvc.perform(post("/api/raids/game/status").param("character", leaderNick).param("fx", "1")
                .contentType(MediaType.TEXT_PLAIN).content("55\t900\t1000\t300\t400\t50\t60"))
            .andReturn().getResponse().getContentAsString();
        assertEquals(12, fx1.split("\n")[1].split("\t", -1).length);  // a DLL que só pede fx=1 não recebe a posição
        for (String bad : List.of("L\t1\t2", "L\t-1\t0\t0", "L\t1\tx\t0", "L\t1\t0\t2000000", "X\t1\t2\t3"))
            mvc.perform(post("/api/raids/game/status").param("character", leaderNick)
                    .contentType(MediaType.TEXT_PLAIN).content("55\t900\t1000\t300\t400\t50\t60\n" + bad))
                .andExpect(status().isBadRequest());
    }

    @Test void invalidEffectsAreRejected() throws Exception {
        raids.create("Raid", as(leaderId));
        for (String effects : List.of("0,3,7,X,Nome", "0,3", "16,3,7,B,Nome", "0,3,7,B,x,Nome", "0,3,7,B,4096,Nome", "0,3,7,B," + "a".repeat(33), "0,3,7,B,Tab\tNo"))
            mvc.perform(post("/api/raids/game/status").param("character", leaderNick)
                    .contentType(MediaType.TEXT_PLAIN).content("50\t1\t2\t1\t2\t1\t2\nE\t" + effects))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/raids/game/status").param("character", leaderNick)
                .contentType(MediaType.TEXT_PLAIN).content("50\t1\t2\t1\t2\t1\t2\nX\tlixo"))
            .andExpect(status().isBadRequest());
    }

    @Test void invalidBodiesAreRejected() throws Exception {
        raids.create("Raid", as(leaderId));
        for (String body : List.of("", "1\t2\t3", "0\t1\t2\t1\t2\t1\t2", "50\t3\t2\t1\t2\t1\t2", "50\tx\t2\t1\t2\t1\t2", "50\t-1\t2\t1\t2\t1\t2"))
            mvc.perform(post("/api/raids/game/status").param("character", leaderNick)
                    .contentType(MediaType.TEXT_PLAIN).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test void siteViewRequiresAnApprovedMember() {
        var raid = raids.create("Raid", as(leaderId));
        jdbc.update("UPDATE member SET status='PENDENTE' WHERE id=?", outsiderId);
        assertThrows(AppException.class, () -> live.forRaid(raid.id(), as(outsiderId)));
    }
}
