package com.guild.app.cw;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.characterstatus.dto.CharacterStatusIngestRequest;
import com.guild.app.characterstatus.service.CharacterStatusService;
import com.guild.app.common.exception.AppException;
import com.guild.app.cw.service.CwAttendanceService;
import com.guild.app.shout.service.ShoutService;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CwAttendanceTest {
    @Autowired CwAttendanceService service;
    @Autowired ShoutService shouts;
    @Autowired CharacterStatusService statusService;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean WebSocketPublisher ws;

    static final String CRAG = "Crag Mind Field";
    static final String END = "[631] : Due to the result of Chip War, Contribution Point has 0 changed.";
    /** 14:00 em Moscou (UTC+3) de 08/11/2026: começo da CW2 (não é dia de PB Major). */
    final Instant t0 = Instant.parse("2026-11-08T11:00:00Z");
    long a, b, c, admin;
    long gid = 5000;

    long member(String role) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id",
                Long.class, "Cw" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), role);
    }
    String nick(long id) { return members.findById(id).orElseThrow().getNickname(); }
    long points(long id) { em.flush(); return jdbc.queryForObject("SELECT points FROM member WHERE id=?", Long.class, id); }
    void loginAs(long id) {
        var p = new MemberPrincipal(members.findById(id).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
    void clock(Instant at) { service.useClock(Clock.fixed(at, ZoneOffset.UTC)); }
    Instant at(Instant base, int seconds) { return base.plusSeconds(seconds); }
    /** Envios a cada 60 s, de {@code from} a {@code to} segundos depois de {@code base}, andando (parado desde agora). */
    void walk(long id, Instant base, int from, int to, String map) {
        for (int s = from; s <= to; s += 60) service.record(id, nick(id), map, at(base, s), at(base, s));
    }
    void objective(String name, long points) {
        jdbc.update("INSERT INTO objective(name,points,type,created_by_id) VALUES(?,?,'NORMAL',?)", name, points, admin);
    }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM cw_award");
        jdbc.update("DELETE FROM cw_session");
        jdbc.update("DELETE FROM cw_sample");
        a = member("MEMBRO");
        b = member("MEMBRO");
        c = member("MEMBRO");
        admin = member("ADMINISTRADOR");
        objective("CW", 15);
        objective("CW Major", 30);
    }
    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
        service.useClock(Clock.systemUTC());
    }

    @Test void windowsAreServerTimeAndPbMajorEveryFourDays() {
        assertEquals(2, CwAttendanceService.slotAt(t0).orElseThrow().window().slot());
        assertTrue(CwAttendanceService.slotAt(t0.minusSeconds(1)).isEmpty());                    // 13:59 Moscou
        var cw3 = CwAttendanceService.slotAt(Instant.parse("2026-11-08T20:59:00Z")).orElseThrow();  // 23:59 Moscou
        assertEquals(3, cw3.window().slot());
        assertEquals(LocalDate.of(2026, 11, 8), cw3.date());
        assertFalse(cw3.major());
        assertTrue(CwAttendanceService.Slot.of(LocalDate.of(2026, 11, 10), 1).major());           // 27/09 + 44 dias
        assertTrue(CwAttendanceService.Slot.of(LocalDate.of(2026, 11, 10), 3).major());
        assertFalse(CwAttendanceService.Slot.of(LocalDate.of(2026, 11, 11), 2).major());
    }

    @Test void cwPointsGoToWhoStayed60PercentWithoutAfk() {
        walk(a, t0, -120, 1260, CRAG);                     // a CW inteira (20 min) andando
        walk(b, t0, 0, 600, CRAG);                         // só metade
        for (int s = -120; s <= 1260; s += 60) service.record(c, nick(c), CRAG, t0.minusSeconds(120), at(t0, s));  // parado: AFK
        long before = points(a);

        clock(at(t0, 200));
        service.gameEnded(nick(b), END);                   // menos de 5 min: aviso atrasado da CW anterior, ignora
        clock(at(t0, 1200));
        assertTrue(service.gameEnded(nick(b), END));
        assertFalse(service.gameEnded(nick(a), END));     // já fechada
        clock(at(t0, 1260));
        service.tick();                                    // ainda esperando os últimos envios
        assertEquals(before, points(a));
        clock(at(t0, 1330));
        service.tick();
        service.tick();                                    // não pontua duas vezes
        assertEquals(before + 15, points(a));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM event_claim WHERE member_id=?", Integer.class, a));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM cw_award WHERE member_id IN (?,?)", Integer.class, b, c));

        loginAs(c);                                        // qualquer membro vê a tabela; só Admin marca o fim
        assertFalse(service.overview(7).canManage());
        loginAs(admin);
        assertTrue(service.overview(7).canManage());
        var cw = service.overview(7).cws().get(0);
        assertEquals("CW2", cw.label());
        assertEquals(at(t0, 1200), cw.endedAt());
        assertEquals("GAME", cw.endSource());
        assertEquals("CW", cw.objectiveName());
        var rowA = cw.members().stream().filter(m -> m.memberId() == a).findFirst().orElseThrow();
        assertEquals(1200, rowA.activeSeconds());
        assertEquals(100, rowA.percent());
        assertTrue(rowA.awarded());
        var rowB = cw.members().stream().filter(m -> m.memberId() == b).findFirst().orElseThrow();
        assertEquals(50, rowB.percent());
        var rowC = cw.members().stream().filter(m -> m.memberId() == c).findFirst().orElseThrow();
        assertEquals(0, rowC.activeSeconds());
        assertEquals(1200, rowC.afkSeconds());
    }

    @Test void afterTheEndNobodyShowsUpAndLastSeenStopsAtTheEnd() {
        walk(a, t0, -60, 2400, CRAG);                      // ficou em Crag depois do fim
        walk(b, t0, 1500, 2400, CRAG);                     // só chegou depois do fim
        clock(at(t0, 1200));
        assertTrue(service.gameEnded(nick(a), END));
        clock(at(t0, 2400));
        loginAs(admin);
        var cw = service.overview(1).cws().get(0);
        assertEquals(1, cw.members().size());
        var row = cw.members().get(0);
        assertEquals(a, row.memberId());
        assertEquals(at(t0, 1200), row.lastSeenAt());
        assertEquals(1200, row.activeSeconds());
    }

    @Test void pbMajorDayGivesCwMajor() {
        Instant major = Instant.parse("2026-11-10T03:00:00Z");   // CW1 de 10/11 (00:00 em Brasília)
        walk(a, major, -60, 900, CRAG);
        clock(at(major, 900));
        assertTrue(service.gameEnded(nick(a), END));
        long before = points(a);
        clock(at(major, 1100));
        service.tick();
        assertEquals(before + 30, points(a));
    }

    @Test void afkCountsFromTheMomentItStoppedAndTimeOutsideDoesNot() {
        walk(a, t0, -60, 300, CRAG);
        for (int s = 360; s <= 780; s += 60) service.record(a, nick(a), CRAG, at(t0, 300), at(t0, s));  // parou em 300
        walk(a, t0, 840, 840, "Cora HQ");                                                            // voltou para a base
        walk(a, t0, 900, 1200, CRAG);
        clock(at(t0, 1200));
        loginAs(admin);
        var row = service.overview(1).cws().get(0).members().get(0);
        // 0-300 andando + 300-360 (o envio de 360 ainda não é AFK, mas o trecho parado vai todo para AFK)
        // 360-780 AFK; 780-900 fora (Cora); 900-1200 andando.
        assertEquals(600, row.activeSeconds());
        assertEquals(480, row.afkSeconds());
    }

    @Test void inTheCwAfkIsOneMinuteThirty() {
        walk(a, t0, -60, 300, CRAG);
        service.record(a, nick(a), CRAG, at(t0, 300), at(t0, 360));   // 60 s parado: ainda não
        service.record(a, nick(a), CRAG, at(t0, 300), at(t0, 420));   // 120 s parado: AFK desde 300
        walk(a, t0, 480, 600, CRAG);
        clock(at(t0, 600));
        loginAs(admin);
        var row = service.overview(1).cws().get(0).members().get(0);
        assertEquals(480, row.activeSeconds());   // 0-300 e 420-600
        assertEquals(120, row.afkSeconds());      // 300-420
    }

    @Test void preBonusTenMinutesBeforeForWhoIsInCrag() {
        statusService.ingest(new CharacterStatusIngestRequest(gid++, nick(a), 50, 1f, 10, 10, 1, 1, 1, 1, CRAG, "Mine", 10, 10), "1.27.1");
        statusService.ingest(new CharacterStatusIngestRequest(gid++, nick(b), 50, 1f, 10, 10, 1, 1, 1, 1, "Cora HQ", "Cora HQ", 10, 10), "1.27.1");
        Instant bonus = t0.minus(Duration.ofMinutes(10));
        jdbc.update("UPDATE character_status SET location_at=? WHERE member_id IN (?,?)", Timestamp.from(bonus.minusSeconds(40)), a, b);
        long beforeA = points(a), beforeB = points(b);
        clock(bonus.plusSeconds(10));
        service.tick();
        service.tick();
        assertEquals(beforeA + 2, points(a));
        assertEquals(beforeB, points(b));
    }

    @Test void withoutEndMessageAnAdministratorEndsIt() {
        walk(a, t0, -60, 900, CRAG);
        clock(Instant.parse("2026-11-08T14:00:30Z"));       // 17:00:30 Moscou: janela da CW2 acabou
        service.tick();
        loginAs(admin);
        var cw = service.overview(1).cws().get(0);
        assertTrue(cw.closed());
        assertNull(cw.awardedAt());
        loginAs(a);
        assertThrows(AppException.class, () -> service.adminEnd(LocalDate.of(2026, 11, 8), 2, at(t0, 900), 1));
        loginAs(admin);
        long before = points(a);
        service.adminEnd(LocalDate.of(2026, 11, 8), 2, at(t0, 900), 1);
        service.tick();
        assertEquals(before + 15, points(a));
        assertThrows(AppException.class, () -> service.adminEnd(LocalDate.of(2026, 11, 8), 2, at(t0, 900), 1));  // já pontuada
    }

    @Test void unknownCharacterCannotEndIt() {
        clock(at(t0, 1200));
        assertThrows(AppException.class, () -> service.gameEnded("NinguemAqui", END));
        assertThrows(AppException.class, () -> service.gameEnded(nick(a), "outra coisa"));
    }

    @Test void atMostTwoCwsAndTwoPreBonusesPerDay() {
        statusService.ingest(new CharacterStatusIngestRequest(gid++, nick(a), 50, 1f, 10, 10, 1, 1, 1, 1, CRAG, "Mine", 10, 10), "1.27.1");
        long before = points(a);
        // CW1, CW2 e CW3 de 08/11 (Moscou 06h, 14h, 22h): presente nas três, e em Crag 10 min antes de cada uma.
        for (Instant start : new Instant[] { Instant.parse("2026-11-08T03:00:00Z"), t0, Instant.parse("2026-11-08T19:00:00Z") }) {
            Instant bonus = start.minus(Duration.ofMinutes(10));
            jdbc.update("UPDATE character_status SET location_at=? WHERE member_id=?", Timestamp.from(bonus), a);
            clock(bonus.plusSeconds(10));
            service.tick();
            walk(a, start, -60, 900, CRAG);
            clock(at(start, 900));
            assertTrue(service.gameEnded(nick(a), END));
            clock(at(start, 1100));
            service.tick();
        }
        assertEquals(before + 2 * 2 + 2 * 15, points(a));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM cw_award WHERE member_id=? AND kind='CW'", Integer.class, a));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM cw_award WHERE member_id=? AND kind='PRE'", Integer.class, a));
    }

    @Test void twoCharactersOnlineCountsTheOneInCrag() {
        // Mesmo membro com dois clientes: o personagem em Crag e outro na base, envios alternados.
        for (int s = -60; s <= 600; s += 60) {
            service.record(a, "NoChip", CRAG, at(t0, s), at(t0, s));
            service.record(a, "NaBase", "Cora HQ", at(t0, s + 30), at(t0, s + 30));
        }
        clock(at(t0, 600));
        loginAs(admin);
        var row = service.overview(1).cws().get(0).members().get(0);
        assertEquals(600, row.activeSeconds());
        assertEquals("NoChip", row.character());
    }

    @Test void standingStillButUsingSkillsOrTakingHitsIsNotAfk() {
        long g = gid++;
        // Parado no mesmo lugar (sem EXP): FP cai (skill) a cada envio -> ativo agora.
        statusService.ingest(new CharacterStatusIngestRequest(g, nick(a), 50, 1f, 100, 100, 100, 100, 100, 100, CRAG, "Chip", 10, 10), "1.28.0");
        jdbc.update("UPDATE character_status SET still_since=now()-interval '10 minutes', active_at=NULL WHERE gid=?", g);
        statusService.ingest(new CharacterStatusIngestRequest(g, nick(a), 50, 1f, 100, 100, 80, 100, 100, 100, CRAG, "Chip", 10, 10), "1.28.0");
        em.flush(); em.clear();
        var still = jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM now()-active_at) FROM character_status WHERE gid=?", Double.class, g);
        assertTrue(still < 5, "FP caiu: ativo agora");
        // Regenerando (FP sobe) não conta como atividade.
        jdbc.update("UPDATE character_status SET active_at=now()-interval '10 minutes' WHERE gid=?", g);
        statusService.ingest(new CharacterStatusIngestRequest(g, nick(a), 50, 1f, 100, 100, 90, 100, 100, 100, CRAG, "Chip", 10, 10), "1.28.0");
        em.flush(); em.clear();
        still = jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM now()-active_at) FROM character_status WHERE gid=?", Double.class, g);
        assertTrue(still > 500, "regeneração não é atividade");
        // DLL 1.30.0: última tecla/câmera há 20 s.
        statusService.ingest(new CharacterStatusIngestRequest(g, nick(a), 50, 1f, 100, 100, 90, 100, 100, 100, CRAG, "Chip", 10, 10, 20), "1.30.0");
        em.flush(); em.clear();
        still = jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM now()-active_at) FROM character_status WHERE gid=?", Double.class, g);
        assertTrue(still > 10 && still <= 20.5, "tecla há 20 s");  // now() é o início da transação
    }

    @Test void rgShoutsFifteenAndTwelveMinutesBefore() {
        clock(t0.minus(Duration.ofMinutes(15)).plusSeconds(5));
        service.tick();
        var first = shouts.forGame(nick(a)).orElseThrow();
        assertEquals(ShoutService.SYSTEM_AUTHOR, first.author());
        assertEquals("RG CW - Pontos de RG serão distribuidos automaticamente em 5 minutos.", first.message());
        clock(t0.minus(Duration.ofMinutes(15)).plusSeconds(35));
        service.tick();                                            // não repete
        assertEquals(first.id(), shouts.forGame(nick(a)).orElseThrow().id());
        clock(t0.minus(Duration.ofMinutes(12)).plusSeconds(5));
        service.tick();
        assertEquals("RG CW - Pontos de RG serão distribuidos automaticamente em 2 minutos.", shouts.forGame(nick(a)).orElseThrow().message());
    }
}
