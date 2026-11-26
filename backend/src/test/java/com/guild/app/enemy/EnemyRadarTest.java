package com.guild.app.enemy;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.enemy.service.EnemyRadarService;
import com.guild.app.member.repository.MemberRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnemyRadarTest {
    @Autowired EnemyRadarService radar;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean WebSocketPublisher ws;
    final List<Long> created = new ArrayList<>();
    long adminId, scoutId, otherId;
    String scoutNick, otherNick;
    static final String VITALS = "50\t10\t800\t0\t300\t0\t40";

    long member(String nick, String role) {
        long id = jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id",
            Long.class, nick, role);
        created.add(id);
        return id;
    }
    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    MemberPrincipal as(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM enemy_guild");
        adminId = member(nick("Adm"), "ADMINISTRADOR");
        scoutNick = nick("Scout");
        scoutId = member(scoutNick, "MEMBRO");
        otherNick = nick("Other");
        otherId = member(otherNick, "MEMBRO");
    }
    @AfterEach void cleanup() {
        // Tirar pela tela apaga também os inimigos vistos, que ficam só em memória.
        for (var g : radar.view(as(adminId)).guilds()) radar.remove(g.id(), as(adminId));
        jdbc.update("DELETE FROM enemy_guild");
        for (long id : created) {
            jdbc.update("DELETE FROM audit_log WHERE actor_id=? OR target_id=?", id, id);
            jdbc.update("DELETE FROM member WHERE id=?", id);
        }
    }

    String report(String character, int fx, String body) throws Exception {
        return mvc.perform(post("/api/raids/game/status").param("character", character).param("fx", String.valueOf(fx))
                .contentType(MediaType.TEXT_PLAIN).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    @Test void onlyAdminsEditTheListAndNamesMustMatchExactly() {
        assertThrows(AppException.class, () -> radar.add(new EnemyRadarService.NewGuild("Betrayed"), as(scoutId)));
        assertThrows(AppException.class, () -> radar.add(new EnemyRadarService.NewGuild("com espaco"), as(adminId)));
        assertThrows(AppException.class, () -> radar.add(new EnemyRadarService.NewGuild("a".repeat(17)), as(adminId)));
        var view = radar.add(new EnemyRadarService.NewGuild(" Betrayed "), as(adminId));
        assertEquals(List.of("Betrayed"), view.guilds().stream().map(EnemyRadarService.EnemyGuild::name).toList());
        assertThrows(AppException.class, () -> radar.add(new EnemyRadarService.NewGuild("Betrayed"), as(adminId)));
        // Maiúsculas contam: o jogo tem guilds de nome parecido.
        radar.add(new EnemyRadarService.NewGuild("betrayed"), as(adminId));
        var memberView = radar.view(as(scoutId));
        assertFalse(memberView.canConfigure());
        assertEquals(2, memberView.guilds().size());
    }

    @Test void enemiesSeenByOneMemberShowUpForEveryNewDll() throws Exception {
        radar.add(new EnemyRadarService.NewGuild("Betrayed"), as(adminId));
        // A DLL nova recebe a lista de guilds mesmo sem inimigo visto.
        String first = report(otherNick, 6, VITALS + "\nL\t1\t0\t0");
        assertTrue(first.startsWith("N\n") && first.contains("W\tBetrayed\n"), first);
        // Quem viu manda o nick, a guild e a posição; guild fora da lista é ignorada.
        report(scoutNick, 6, VITALS + "\nL\t3\t100\t200\nX\tJudas\tBetrayed\t110\t-220\nX\tPik\tLegacy\t1\t2\nX\tFake\tBetrayeb\t5\t5");
        String seen = report(otherNick, 6, VITALS + "\nL\t1\t0\t0");
        assertTrue(seen.contains("X\tJudas\tBetrayed\t3,110,-220\t"), seen);
        assertFalse(seen.contains("Pik") || seen.contains("Fake"), seen);
        // DLL antiga (fx=5) não recebe as linhas novas, que ela recusaria.
        String old = report(otherNick, 5, VITALS + "\nL\t1\t0\t0");
        assertFalse(old.contains("W\t") || old.contains("X\t"), old);
        // O site mostra quem foi visto e quem viu.
        var sighting = radar.view(as(otherId)).sightings().getFirst();
        assertEquals("Judas", sighting.nick());
        assertEquals(scoutNick, sighting.seenBy());
        // Sem posição de quem viu, não há mapa para o ponto: o envio é ignorado.
        report(scoutNick, 6, VITALS + "\nX\tMaterWelon\tBetrayed\t1\t1");
        assertFalse(report(otherNick, 6, VITALS).contains("MaterWelon"));
        // Tirar a guild da lista apaga os pontos dela.
        long id = radar.view(as(adminId)).guilds().getFirst().id();
        radar.remove(id, as(adminId));
        String after = report(otherNick, 6, VITALS);
        assertFalse(after.contains("Judas") || after.contains("W\t"), after);
    }

    String marker(String character, String dead) throws Exception {
        var request = get("/api/marker/game").param("character", character);
        if (dead != null) request = request.param("dead", dead);
        var result = mvc.perform(request).andReturn().getResponse();
        return result.getStatus() == 204 ? null : result.getContentAsString().strip();
    }

    @Test void theArrowGoesByItselfToTheClosestEnemyUntilHeDies() throws Exception {
        String adminNick = members.findById(adminId).orElseThrow().getNickname();
        mvc.perform(post("/api/marker/game").param("character", adminNick).contentType("text/plain").content("clear"));
        assertNull(marker(otherNick, null), "sem guild na lista, sem seta");
        radar.add(new EnemyRadarService.NewGuild("Betrayed"), as(adminId));
        // Sem serial (DLL 1.53.x) o inimigo aparece no mapa, mas não ganha seta.
        report(scoutNick, 6, VITALS + "\nL\t3\t0\t0\nX\tOld\tBetrayed\t1\t1");
        assertNull(marker(otherNick, null));
        // Vai no mais perto de quem viu.
        report(scoutNick, 6, VITALS + "\nL\t3\t0\t0\nX\tJudas\tBetrayed\t100\t0\t111\nX\tMaterWelon\tBetrayed\t50\t0\t222");
        String first = marker(otherNick, null);
        assertTrue(first.endsWith("\t0\t222"), first);
        // Fica nele enquanto alguém o vê, mesmo que outro chegue mais perto.
        report(scoutNick, 6, VITALS + "\nL\t3\t0\t0\nX\tJudas\tBetrayed\t10\t0\t111\nX\tMaterWelon\tBetrayed\t50\t0\t222");
        assertEquals(first, marker(otherNick, null));
        // Uma DLL viu o alvo morto: a seta pula para o próximo, com id novo.
        String next = marker(otherNick, first.split("\t")[0]);
        assertTrue(next.endsWith("\t0\t111"), next);
        assertNotEquals(first.split("\t")[0], next.split("\t")[0]);
        // A marcação manual ganha; quando o alvo dela morre, a automática volta.
        mvc.perform(post("/api/marker/game").param("character", adminNick).contentType("text/plain").content("1\t5"))
            .andExpect(status().isNoContent());
        String manual = marker(otherNick, null);
        assertTrue(manual.endsWith("\t1\t5"), manual);
        assertTrue(marker(otherNick, manual.split("\t")[0]).endsWith("\t0\t111"));
        // Serial inválido é recusado.
        mvc.perform(post("/api/raids/game/status").param("character", scoutNick).param("fx", "6")
                .contentType(MediaType.TEXT_PLAIN).content(VITALS + "\nL\t3\t0\t0\nX\tJudas\tBetrayed\t1\t1\t-5"))
            .andExpect(status().isBadRequest());
    }

    @Test void malformedEnemyLinesAreRejected() throws Exception {
        for (String bad : List.of("X\tJudas\tBetrayed\t1", "X\t\tBetrayed\t1\t2", "X\tJu das\tBetrayed\t1\t2",
                "X\tJudas\tBetrayed\tx\t2", "X\tJudas\tBetrayed\t1\t2000000", "X\t" + "a".repeat(17) + "\tBetrayed\t1\t2"))
            mvc.perform(post("/api/raids/game/status").param("character", scoutNick).param("fx", "6")
                    .contentType(MediaType.TEXT_PLAIN).content(VITALS + "\nL\t1\t0\t0\n" + bad))
                .andExpect(status().isBadRequest());
        // Até 100 inimigos por envio.
        var many = new StringBuilder(VITALS + "\nL\t1\t0\t0");
        for (int i = 0; i < 100; i++) many.append("\nX\tE").append(i).append("\tBetrayed\t").append(i).append("\t0");
        report(scoutNick, 6, many.toString());
        many.append("\nX\tE100\tBetrayed\t0\t0");
        mvc.perform(post("/api/raids/game/status").param("character", scoutNick).param("fx", "6")
                .contentType(MediaType.TEXT_PLAIN).content(many.toString()))
            .andExpect(status().isBadRequest());
    }
}
