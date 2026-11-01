package com.guild.app.shout;

import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.shout.service.ShoutService;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShoutTest {
    @Autowired ShoutService service;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean WebSocketPublisher ws;
    // O servico guarda o ultimo shout em memoria: cada teste anda o relogio para nao herdar o anterior.
    static Instant base = Instant.parse("2030-01-01T00:00:00Z");
    Instant now;
    String leaderNick, adminNick, moderatorNick, memberNick, pendingNick;

    String member(String nick, String role, String status) {
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true)", nick, role, status);
        return nick;
    }
    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    void at(Instant instant) throws Exception {
        Field f = ShoutService.class.getDeclaredField("clock"); f.setAccessible(true);
        f.set(org.springframework.test.util.AopTestUtils.getTargetObject(service), Clock.fixed(instant, ZoneOffset.UTC));
    }
    org.springframework.test.web.servlet.ResultActions shout(String character, String message) throws Exception {
        var request = post("/api/shout/game").contentType("text/plain;charset=UTF-8");
        if (character != null) request.param("character", character);
        return mvc.perform(request.content(message.getBytes(StandardCharsets.UTF_8)));
    }

    @BeforeEach void setup() throws Exception {
        base = base.plusSeconds(3600);
        now = base;
        at(now);
        // Um lider so por banco (idx_single_leader): usa o existente (se aprovado) ou cria.
        leaderNick = members.findByRole(Role.LIDER).map(com.guild.app.member.entity.Member::getNickname)
            .orElseGet(() -> member(nick("Lead"), "LIDER", "APROVADO"));
        jdbc.update("UPDATE member SET status='APROVADO',profile_complete=true,deleted_at=null,suspended_at=null WHERE role='LIDER'");
        adminNick = member(nick("Adm"), "ADMINISTRADOR", "APROVADO");
        moderatorNick = member(nick("Mod"), "MODERADOR", "APROVADO");
        memberNick = member(nick("Mia"), "MEMBRO", "APROVADO");
        pendingNick = member(nick("Pend"), "MEMBRO", "PENDENTE");
    }
    @AfterEach void resetClock() throws Exception { at(Instant.now()); }

    @Test void onlyLeaderAndAdminCharactersCanShout() throws Exception {
        mvc.perform(get("/api/shout/game/sender").param("character", adminNick.toLowerCase())).andExpect(status().isNoContent());
        mvc.perform(get("/api/shout/game/sender").param("character", leaderNick)).andExpect(status().isNoContent());
        mvc.perform(get("/api/shout/game/sender").param("character", moderatorNick)).andExpect(status().isNotFound());
        mvc.perform(get("/api/shout/game/sender").param("character", memberNick)).andExpect(status().isNotFound());
        mvc.perform(get("/api/shout/game/sender")).andExpect(status().isNotFound());

        shout(memberNick, "oi").andExpect(status().isForbidden());
        shout(moderatorNick, "oi").andExpect(status().isForbidden());
        shout(null, "oi").andExpect(status().isForbidden());
        shout(adminNick, "Todos no HQ").andExpect(status().isNoContent());
        at(now.plusSeconds(11));
        shout(leaderNick, "Agora!").andExpect(status().isNoContent());
    }

    @Test void approvedMembersSeeTheShoutForOneMinute() throws Exception {
        shout(adminNick, "  Todos\tno\nHQ às 22h  ").andExpect(status().isNoContent());
        var shown = mvc.perform(get("/api/shout/game").param("character", memberNick.toUpperCase()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var parts = shown.split("\t");
        assertEquals(3, parts.length);
        assertTrue(Long.parseLong(parts[0]) >= now.toEpochMilli());
        assertEquals(adminNick, parts[1]);
        assertEquals("Todos no HQ às 22h\n", parts[2]);
        mvc.perform(get("/api/shout/game").param("character", pendingNick)).andExpect(status().isNoContent());
        mvc.perform(get("/api/shout/game").param("character", "Ninguem")).andExpect(status().isNoContent());
        mvc.perform(get("/api/shout/game")).andExpect(status().isNoContent());
        at(now.plusSeconds(60));
        mvc.perform(get("/api/shout/game").param("character", memberNick)).andExpect(status().isNoContent());
    }

    @Test void validatesLengthAndCooldown() throws Exception {
        shout(adminNick, "   ").andExpect(status().isBadRequest());
        shout(adminNick, "x".repeat(121)).andExpect(status().isBadRequest());
        var first = service.send(adminNick, "x".repeat(120));
        at(now.plusSeconds(5));
        shout(leaderNick, "de novo").andExpect(status().isConflict());
        at(now.plusSeconds(10));
        assertTrue(service.send(leaderNick, "de novo").id() > first.id());
        assertThrows(AppException.class, () -> service.send(memberNick, "oi"));
    }
}
