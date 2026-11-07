package com.guild.app.event;

import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventGameTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.guild.app.event.service.EventGameService service;
    @MockitoBean WebSocketPublisher ws;

    String memberNick, pendingNick, password;
    long memberId, objectiveId, eventId;

    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }

    long member(String nick, String status) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'MEMBRO',?,true) RETURNING id",
            Long.class, nick, status);
    }

    long event(long objective, String pass, String expiresIn, boolean active) {
        return jdbc.queryForObject("INSERT INTO guild_event(objective_id,password,duration_minutes,created_by_id,expires_at,active) "
            + "VALUES(?,?,5,?,NOW() + CAST(? AS INTERVAL),?) RETURNING id", Long.class, objective, pass, memberId, expiresIn, active);
    }

    ResultActions claim(long id, String character, String pass) throws Exception {
        var request = post("/api/events/game/" + id + "/claim").contentType("text/plain;charset=UTF-8");
        if (character != null) request.param("character", character);
        return mvc.perform(request.content(pass.getBytes(StandardCharsets.UTF_8)));
    }

    @BeforeEach void setup() {
        // Eventos de outros testes no mesmo banco ficam de fora: so os deste teste estao ativos.
        jdbc.update("UPDATE guild_event SET active=false");
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(service);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(target, "forget");
        memberNick = nick("Eva");
        pendingNick = nick("Pen");
        memberId = member(memberNick, "APROVADO");
        member(pendingNick, "PENDENTE");
        objectiveId = jdbc.queryForObject("INSERT INTO objective(name,points,type,created_by_id) VALUES(?,?,'NORMAL',?) RETURNING id",
            Long.class, "Guerra\tde HQ", 150L, memberId);
        password = "Ab1!";
        eventId = event(objectiveId, password, "10 minutes", true);
    }

    @Test void listsPendingEventsForApprovedCharacterOnly() throws Exception {
        mvc.perform(get("/api/events/game").param("character", memberNick.toUpperCase()))
            .andExpect(status().isOk())
            .andExpect(content().string(startsWith(eventId + "\t")))
            .andExpect(content().string(endsWith("\t150\tGuerra de HQ\n")));
        mvc.perform(get("/api/events/game").param("character", pendingNick)).andExpect(status().isNoContent());
        mvc.perform(get("/api/events/game").param("character", "ninguem")).andExpect(status().isNoContent());
        mvc.perform(get("/api/events/game")).andExpect(status().isNoContent());
    }

    @Test void claimGivesPointsOnceAndHidesTheEvent() throws Exception {
        claim(eventId, memberNick, password).andExpect(status().isOk())
            .andExpect(content().string("150\tGuerra de HQ\n"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM event_claim WHERE event_id=? AND member_id=?",
            Integer.class, eventId, memberId));
        assertEquals("GAME", jdbc.queryForObject(
            "SELECT payload->>'source' FROM audit_log WHERE type='EVENT_CLAIMED' AND target_id=? ORDER BY id DESC LIMIT 1",
            String.class, memberId));
        claim(eventId, memberNick, password).andExpect(status().isConflict())
            .andExpect(content().string(containsString("já resgatou")));
        mvc.perform(get("/api/events/game").param("character", memberNick)).andExpect(status().isNoContent());
    }

    @Test void claimErrorsComeBackAsText() throws Exception {
        claim(eventId, memberNick, "zzzz").andExpect(status().isBadRequest()).andExpect(content().string("Senha inválida"));
        claim(eventId, memberNick, "abc").andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("4 caracteres")));
        claim(eventId, pendingNick, password).andExpect(status().isForbidden());
        claim(eventId, null, password).andExpect(status().isForbidden());
        long expired = event(objectiveId, password, "-1 minutes", true);
        claim(expired, memberNick, password).andExpect(status().isBadRequest()).andExpect(content().string("Evento expirado"));
        claim(999_999_999L, memberNick, password).andExpect(status().isNotFound());
    }

    @Test void tooManyWrongPasswordsLocksForAMinute() throws Exception {
        for (int i = 0; i < 5; i++) claim(eventId, memberNick, "zzzz").andExpect(status().isBadRequest());
        claim(eventId, memberNick, password).andExpect(status().isTooManyRequests());
    }
}
