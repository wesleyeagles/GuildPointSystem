package com.guild.app.pbstatus;

import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Status de PB da kbase no jogo: pagina sem duplicatas, mapas com o nome do cliente, so membro aprovado, serial. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PbStatusTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PbStatusService service;
    @MockitoBean PbStatusSource source;
    @MockitoBean WebSocketPublisher ws;

    static String row(String code, String name, String status, String when) {
        return "<tr class=\"dataList-row\"><td class=\"dataList-cell\"><a href=\"/kbase/monsters/" + code + "\">" + name
             + "</a></td><td class=\"dataList-cell red\"><span class=\"deadstatus\"> " + status + " </span></td>"
             + "<td class=\"dataList-cell\"> " + when + " </td></tr>";
    }
    static String region(String name) {
        return "<tr class=\"dataList-row\"><td class=\"dataList-cell block-header text-center\" colspan=\"3\">" + name + "</td></tr>"
             + "<tr class=\"dataList-row\"><td class=\"dataList-cell text-left table-header\">Имя</td>"
             + "<td class=\"dataList-cell text-left table-header\">Статус</td><td class=\"dataList-cell text-left table-header\">Возрождение</td></tr>";
    }
    static String page(String... rows) {
        return "<html><body><table class=\"dataList-table\">" + String.join("", rows) + "</table></body></html>";
    }

    /** Recorte da pagina real de 16/11/2026: Belphegor 3x no Vulcao, Trasher vivo, PB de guilda em dois mapas. */
    static final String REAL = page(
        region("Вулкан"),
        row("06D07", "[60]Бельфегор", "Killed by Liberty\n [Espada]", "2026-11-17 00:00:00"),
        row("26BA4", "[ПБ Гильдий] Опытный Орк", "Killed by Lambada [Espada]", "2026-11-17 00:00:00"),
        row("06D07", "[60]Бельфегор", "Killed by Liberty [Espada]", "2026-11-21 20:00:00"),
        row("06D07", "[60]Бельфегор", "Killed by Liberty [Espada]", "2026-11-17 20:00:00"),
        region("Анакаде"),
        row("11507", "[63]Трашер", "Live", "Не запланировано"),
        row("02D05", "[45]Король Трицератопсов", "Killed by Xzbit [FallenAnge1s]", "2026-11-17 20:00:00"),
        row("02D05", "[45]Король Трицератопсов", "Killed by Xzbit [FallenAnge1s]", "2026-11-17 00:00:00"),
        region("Солус"),
        row("93400", "[ПБ Гильдий]РазрушительУпсилон", "Dead", "2026-11-17 00:00:00"),
        region("Харам"),
        row("93400", "[ПБ Гильдий]РазрушительУпсилон", "Dead", "2026-11-17 00:00:00"));

    String character;

    @BeforeEach void setup() throws Exception {
        character = "Pb" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true)",
                character, "MEMBRO", "APROVADO");
        var target = AopTestUtils.<PbStatusService>getTargetObject(service);
        var snapshot = PbStatusService.class.getDeclaredField("snapshot"); snapshot.setAccessible(true);
        snapshot.set(target, new PbStatusService.Snapshot(0, Instant.EPOCH, List.of(), ""));
        var wanted = PbStatusService.class.getDeclaredField("wantedUntil"); wanted.setAccessible(true);
        wanted.set(target, Instant.EPOCH);
        reset(source);
    }

    String game(String who, Long since) throws Exception {
        var req = get("/api/pb-status/game").param("character", who);
        if (since != null) req = req.param("since", since.toString());
        return mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    static long msk(String when) {
        return LocalDateTime.parse(when.replace(' ', 'T')).atZone(PbStatusPage.SERVER_ZONE).toEpochSecond();
    }

    @Test void parseJoinsRepeatedScheduleRowsPerMap() {
        var bosses = PbStatusPage.dedupe(PbStatusPage.parse(REAL));
        assertEquals(6, bosses.size());
        var belphegor = bosses.get(0);
        assertEquals("Volcano", belphegor.map());
        assertEquals("06D07", belphegor.code());
        assertEquals(PbStatusPage.State.KILLED, belphegor.state());
        assertEquals("Liberty", belphegor.killer());
        assertEquals("Espada", belphegor.guild());
        assertEquals(msk("2026-11-17 00:00:00"), belphegor.respawn().getEpochSecond(), "normal: 18:00 de Brasilia");
        assertEquals(msk("2026-11-17 20:00:00"), belphegor.extra().getEpochSecond(), "extra: o proximo das 14:00, nao o de sabado");
        assertNull(bosses.get(1).extra(), "so um horario: sem extra");
        assertTrue(bosses.get(1).guildPb());
        assertEquals(PbStatusPage.State.LIVE, bosses.get(2).state());
        assertNull(bosses.get(2).respawn());
        assertEquals(msk("2026-11-17 00:00:00"), bosses.get(3).respawn().getEpochSecond(), "extra listado primeiro nao vira o normal");
        assertEquals(msk("2026-11-17 20:00:00"), bosses.get(3).extra().getEpochSecond());
        assertEquals(List.of("Solus", "Haram"), bosses.subList(4, 6).stream().map(PbStatusPage.Boss::map).toList(),
                "o mesmo PB de guilda em mapas diferentes nao e duplicata");
        assertEquals(PbStatusPage.State.DEAD, bosses.get(4).state());
    }

    @Test void elanOnlyAtFourteenHasNoExtra() {
        var bosses = PbStatusPage.dedupe(PbStatusPage.parse(page(region("Элан"),
                row("0AB07", "[61]Каменолицый", "Killed by A [B]", "2026-11-17 20:00:00"),
                row("0AB07", "[61]Каменолицый", "Killed by A [B]", "2026-11-23 20:00:00"))));
        assertEquals(msk("2026-11-17 20:00:00"), bosses.get(0).respawn().getEpochSecond());
        assertNull(bosses.get(0).extra());
    }

    @Test void otherHoursFallBackToFirstRowAsRegular() {
        var bosses = PbStatusPage.dedupe(PbStatusPage.parse(page(region("Солус"),
                row("01A05", "[45]x", "Dead", "2026-11-18 08:00:00"),
                row("01A05", "[45]x", "Dead", "2026-11-17 16:00:00"),
                row("01A05", "[45]x", "Dead", "2026-11-17 08:00:00"))));
        assertEquals(msk("2026-11-17 08:00:00"), bosses.get(0).respawn().getEpochSecond());
        assertEquals(msk("2026-11-17 16:00:00"), bosses.get(0).extra().getEpochSecond());
    }

    @Test void liveWinsOverScheduledRow() {
        var bosses = PbStatusPage.dedupe(PbStatusPage.parse(page(region("Элан"),
                row("0AB07", "[61]Каменолицый", "Killed by A [B]", "2026-11-17 20:00:00"),
                row("0AB07", "[61]Каменолицый", "Live", "Не запланировано"))));
        assertEquals(1, bosses.size());
        assertEquals(PbStatusPage.State.LIVE, bosses.get(0).state());
        assertEquals("Elan Plateau", bosses.get(0).map());
    }

    @Test void gameListsMapsAndBossesOnceThenOnlySerialUntilChange() throws Exception {
        when(source.page()).thenReturn(Optional.of(REAL));
        game(character, null);  // a primeira leitura da kbase vai em segundo plano: a resposta nao espera
        assertTrue(service.refresh());
        String body = game(character, null);
        var lines = body.split("\n");
        assertTrue(lines[0].matches("S\t1\t\\d+"), lines[0]);
        assertEquals("M\tVolcano", lines[1]);
        assertEquals("B\t06D07\t[60]Бельфегор\tK\tLiberty\tEspada\t" + msk("2026-11-17 00:00:00") + "\t0\t"
                + msk("2026-11-17 20:00:00"), lines[2]);
        assertEquals(1, body.lines().filter(l -> l.startsWith("B\t06D07\t")).count(), "sem duplicata");
        assertTrue(body.contains("B\t11507\t[63]Трашер\tL\t\t\t0\t0\t0\n"));
        assertTrue(body.contains("M\tSolus\nB\t93400\t[ПБ Гильдий]РазрушительУпсилон\tD\t\t\t" + msk("2026-11-17 00:00:00") + "\t1\t0\n"));

        assertEquals("S\t1\t0\n", game(character, 1L), "sem mudanca: so o serial");

        // A mesma pagina lida de novo nao muda o serial; uma morte nova muda.
        service.refresh();
        assertEquals("S\t1\t0\n", game(character, 1L));
        when(source.page()).thenReturn(Optional.of(REAL.replace("\"> Live <", "\"> Killed by Zed [QUlT] <")));
        service.refresh();
        String changed = game(character, 1L);
        assertTrue(changed.startsWith("S\t2\t"));
        assertTrue(changed.contains("B\t11507\t[63]Трашер\tK\tZed\tQUlT\t0\t0\t0\n"));
    }

    @Test void kbaseDownKeepsLastGoodList() throws Exception {
        when(source.page()).thenReturn(Optional.of(REAL));
        game(character, null);  // a primeira leitura da kbase vai em segundo plano: a resposta nao espera
        assertTrue(service.refresh());
        game(character, null);
        when(source.page()).thenReturn(Optional.empty());
        assertFalse(service.refresh());
        when(source.page()).thenReturn(Optional.of("<html>manutencao</html>"));
        assertFalse(service.refresh());
        assertTrue(game(character, null).contains("B\t06D07\t"));
    }

    @Test void onlyApprovedMembers() throws Exception {
        String pending = "Pp" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true)",
                pending, "MEMBRO", "PENDENTE");
        mvc.perform(get("/api/pb-status/game").param("character", pending)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pb-status/game").param("character", "ninguem-" + UUID.randomUUID().toString().substring(0, 4)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pb-status/game")).andExpect(status().isForbidden());
        verify(source, never()).page();
    }
}
