package com.guild.app.resource;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.resource.service.ResourceSpawnService;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GrTrackingTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean WebSocketPublisher ws;
    @Autowired ResourceSpawnService service;
    @Autowired MemberRepository members;
    long viewerId, senderId, adminId;
    String viewerNick, senderNick, pendingNick;
    long serial = 900_000_000L + (long) (Math.random() * 1_000_000);

    long member(String nick, String status, String role) {
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true)", nick, role, status);
        return jdbc.queryForObject("SELECT id FROM member WHERE nickname=?", Long.class, nick);
    }
    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    MemberPrincipal as(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }

    ResultActions send(String character, int map, long since, String body) throws Exception {
        var request = post("/api/resources/game").contentType("text/plain;charset=UTF-8")
            .param("map", String.valueOf(map)).param("since", String.valueOf(since));
        if (character != null) request.param("character", character);
        return mvc.perform(request.content(body.getBytes(StandardCharsets.UTF_8)));
    }
    String ok(String character, int map, long since, String body) throws Exception {
        return send(character, map, since, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
    long spawns(String name) {
        return jdbc.queryForObject("SELECT count(*) FROM resource_spawn WHERE mob_name=? AND serial>=900000000", Long.class, name);
    }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM resource_spawn");
        viewerNick = nick("Vie");
        viewerId = member(viewerNick, "APROVADO", "MEMBRO");
        senderNick = nick("Sen");
        senderId = member(senderNick, "APROVADO", "MEMBRO");
        pendingNick = nick("Pen");
        member(pendingNick, "PENDENTE", "MEMBRO");
        adminId = member(nick("Adm"), "APROVADO", "ADMINISTRADOR");
        jdbc.update("INSERT INTO resource_tracker(member_id) VALUES(?)", viewerId);
    }

    @Test void separateAccessCatalogHistoryAndDedup() throws Exception {
        String name="[gr4](typeA) Mech. Warrior Exec";
        String body="G\t"+serial+"\t66581\t100\t-20\t200\t"+name;
        assertEquals("A\t0\n",ok(viewerNick,55,0,body));
        assertEquals(1,spawns(name));
        service.updateGrAccess(List.of(senderId),as(adminId));
        String result=ok(senderNick,55,0,body);
        assertTrue(result.startsWith("A\t1\n"));assertTrue(result.contains("I\t"));assertTrue(result.contains(name));
        assertEquals(1,spawns(name));
        var h=service.history(55,0,500,as(adminId));assertEquals(1,h.entries().size());
        assertEquals(66581,h.entries().getFirst().code());assertEquals(-20,h.entries().getFirst().y());
        assertTrue(ok(viewerNick,11,0,"").startsWith("A\t1\n"));
        assertEquals("A\t0\n",ok(viewerNick,55,0,""));
        service.updateGrAccess(List.of(),as(adminId));assertEquals("A\t0\n",ok(senderNick,55,0,""));
    }
    @Test void rejectsUnknownCatalogAndNeverInfersDeath() throws Exception {
        String name="[gr4](typeA) Mech. Warrior Exec";
        ok(senderNick,55,0,"G\t"+serial+"\t99999\t1\t2\t3\t"+name);assertEquals(0,spawns(name));
        ok(senderNick,55,0,"S\t"+serial+"\t1\t3\t"+name);assertEquals(0,spawns(name));
        ok(senderNick,55,0,"G\t"+serial+"\t66581\t1\t2\t3\t"+name);
        ok(senderNick,55,0,"D\t"+serial+"\t"+name);
        assertNull(service.history(55,0,500,as(adminId)).entries().getFirst().diedAt());
        assertThrows(AppException.class,()->service.updateGrAccess(List.of(senderId),as(senderId)));
        send(pendingNick,55,0,"").andExpect(status().isForbidden());
    }
    @Test void adminAccessEndpointAndAllGrades() throws Exception {
        mvc.perform(put("/api/resources/gr-access").with(user(as(senderId))).contentType("application/json").content("{\"memberIds\":[]}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/resources/gr-access").with(user(as(adminId))).contentType("application/json").content("{\"memberIds\":["+senderId+"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.grTrackers[0].nickname").value(senderNick));
        for (var entry : com.guild.app.resource.service.GrCatalog.MOBS.entrySet()) {
            String row="G\t"+(serial+entry.getKey())+"\t"+entry.getKey()+"\t100\t20\t200\t"+entry.getValue().name();
            String response=ok(senderNick,55,0,row);
            assertTrue(response.contains("\t"+entry.getValue().category()+"\t"));
        }
        assertEquals(9,service.history(55,0,500,as(adminId)).entries().size());
    }
}
