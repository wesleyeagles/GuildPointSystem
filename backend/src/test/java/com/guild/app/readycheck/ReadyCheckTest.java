package com.guild.app.readycheck;

import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.readycheck.service.ReadyCheckService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Check de "Pronto?" da PT: o líder chama pela DLL, a PT responde, o tempo acaba e a janela some. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReadyCheckTest {
    @Autowired ReadyCheckService service;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    static Instant base = Instant.parse("2032-01-01T00:00:00Z");
    Instant now;
    String leader, pending, a, b;

    String member(String status) {
        String nick = "Rc" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'MEMBRO',?,true)", nick, status);
        return nick;
    }
    void at(Instant instant) {
        now = instant;
        Object target = AopTestUtils.getTargetObject(service);
        ReflectionTestUtils.setField(target, "clock", Clock.fixed(instant, ZoneOffset.UTC));
    }
    void later(long seconds) { at(now.plusSeconds(seconds)); }
    String party(String... nicks) { return String.join("\n", nicks) + "\n"; }

    @BeforeEach void setup() {
        base = base.plusSeconds(3600);
        at(base);
        leader = member("APROVADO");
        pending = member("PENDENTE");
        a = "Fora" + UUID.randomUUID().toString().substring(0, 6);  // integrantes da PT nao precisam ser do site
        b = "Outro" + UUID.randomUUID().toString().substring(0, 6);
    }
    @AfterEach void clear() { at(Instant.now()); }

    @Test void leaderStartsEveryoneAnswersAndTheResultStaysForAFewSeconds() {
        String started = service.start(leader, party(leader, a, b));
        long id = Long.parseLong(started.split("\t")[0]);
        assertEquals(id + "\t" + leader + "\t30\tOPEN\n" + leader + "\tY\n" + a + "\t?\n" + b + "\t?\n", started);
        // Cada integrante ve o mesmo check (nick sem diferenciar maiusculas); quem esta fora nao ve nada.
        assertEquals(started, service.current(a.toUpperCase()));
        assertNull(service.current("Ninguem"));
        later(10);
        assertTrue(service.answer(id, a, "Y").contains(a + "\tY\n"));
        String done = service.answer(id, b, "N");
        assertTrue(done.startsWith(id + "\t" + leader + "\t0\tDONE\n") && done.endsWith(b + "\tN\n"));
        // Resposta depois do fim nao muda nada; resultado fica 8 s e some.
        assertEquals(done, service.answer(id, b, "Y"));
        later(7);
        assertEquals(done, service.current(leader));
        later(1);
        assertNull(service.current(leader));
        assertThrows(AppException.class, () -> service.answer(id, a, "Y"));
    }

    @Test void whoDoesNotAnswerInThirtySecondsIsMarkedAway() {
        long id = Long.parseLong(service.start(leader, party(leader, a, b)).split("\t")[0]);
        service.answer(id, a, "Y");
        later(29);
        assertTrue(service.current(b).contains("\t1\tOPEN\n"));
        later(1);
        String done = service.current(b);
        assertTrue(done.contains("\t0\tDONE\n") && done.contains(a + "\tY\n") && done.endsWith(b + "\tA\n"));
        later(8);
        service.sweep();
        assertNull(service.current(a));
    }

    @Test void onlyAnApprovedLeaderListedFirstCanStart() {
        assertThrows(AppException.class, () -> service.start(leader, party(a, leader)));     // lider nao e o primeiro
        assertThrows(AppException.class, () -> service.start(pending, party(pending, a)));   // membro pendente
        assertThrows(AppException.class, () -> service.start(a, party(a, b)));               // nao e do site
        assertThrows(AppException.class, () -> service.start(leader, party(leader)));        // sozinho
        assertThrows(AppException.class, () -> service.start(leader, "x\n"));
        // Nicks invalidos e repetidos saem; no maximo 8.
        String started = service.start(leader, party(leader, "a b", a, a.toLowerCase(), "!!", b));
        assertEquals(3, started.split("\n").length - 1);
        long id = Long.parseLong(started.split("\t")[0]);
        assertThrows(AppException.class, () -> service.answer(id, "Ninguem", "Y"));
    }

    @Test void clickingAgainRightAwayKeepsTheSameCheckButLaterStartsANewOne() {
        String first = service.start(leader, party(leader, a));
        later(2);
        assertEquals(first.split("\t")[0], service.start(leader, party(leader, a)).split("\t")[0]);
        later(2);
        String second = service.start(leader, party(leader, a, b));
        assertNotEquals(first.split("\t")[0], second.split("\t")[0]);
        assertEquals(second.replace("\t30\t", "\t30\t"), service.current(b));
        assertTrue(service.current(a).startsWith(second.split("\t")[0] + "\t"));
    }

    @Test void dllRoutesAreOpenAndAnswerInPlainText() throws Exception {
        mvc.perform(get("/api/ready-check/game").param("character", leader)).andExpect(status().isNoContent());
        String started = mvc.perform(post("/api/ready-check/game/start").param("character", leader)
                        .contentType(MediaType.TEXT_PLAIN).content(party(leader, a)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(started.split("\t")[0]);
        mvc.perform(get("/api/ready-check/game").param("character", a)).andExpect(status().isOk());
        mvc.perform(post("/api/ready-check/game/" + id + "/answer").param("character", a)
                .contentType(MediaType.TEXT_PLAIN).content("Y")).andExpect(status().isOk());
        mvc.perform(post("/api/ready-check/game/start").param("character", a)
                .contentType(MediaType.TEXT_PLAIN).content(party(a, leader))).andExpect(status().isForbidden());
    }
}
