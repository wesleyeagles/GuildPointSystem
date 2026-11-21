package com.guild.app.marker;

import com.guild.app.member.repository.MemberRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MarkerGameTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired MarkerService service;
    @MockitoBean WebSocketPublisher ws;
    @MockitoBean com.guild.app.translate.service.ChatTranslator translator;
    String adminNick, memberNick, pendingNick;

    String member(String role, String status) {
        String nick = "Mk" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete,points) VALUES(?,?,?,true,0)", nick, role, status);
        return nick;
    }

    @BeforeEach void setup() {
        adminNick = member("ADMINISTRADOR", "APROVADO");
        memberNick = member("MEMBRO", "APROVADO");
        pendingNick = member("MEMBRO", "PENDENTE");
        service.forget();
    }

    ResultActions mark(String character, String body) throws Exception {
        return mvc.perform(post("/api/marker/game").param("character", character).contentType("text/plain;charset=UTF-8")
            .content(body.getBytes(StandardCharsets.UTF_8)));
    }

    String read(String character) throws Exception {
        var result = mvc.perform(get("/api/marker/game").param("character", character)).andReturn().getResponse();
        return result.getStatus() == 204 ? null : result.getContentAsString();
    }

    @Test void adminMarksAndApprovedMembersSeeIt() throws Exception {
        mark(adminNick.toLowerCase(), "1\t1857751").andExpect(status().isNoContent());
        String seen = read(memberNick);
        assertNotNull(seen);
        String[] parts = seen.strip().split("\t");
        assertEquals("1", parts[1]);
        assertEquals("1857751", parts[2]);
        assertNull(read(pendingNick), "pendente não vê");
        assertNull(read("ninguem"));
    }

    @Test void clearKeepsANewIdWithKindMinusOne() throws Exception {
        mark(adminNick, "0\t42").andExpect(status().isNoContent());
        String first = read(memberNick);
        mark(adminNick, "clear").andExpect(status().isNoContent());
        assertNull(read(memberNick), "sem seta: 204");
        mark(adminNick, "0\t42").andExpect(status().isNoContent());
        assertNotEquals(first.split("\t")[0], read(memberNick).split("\t")[0], "cada marcação tem id novo");
    }

    @Test void onlyLeaderOrAdminMarksAndBadTargetsAreRejected() throws Exception {
        mark(memberNick, "1\t5").andExpect(status().isForbidden());
        mark(adminNick, "2\t5").andExpect(status().isBadRequest());
        mark(adminNick, "1\t0").andExpect(status().isBadRequest());
        mark(adminNick, "x").andExpect(status().isBadRequest());
    }
}
