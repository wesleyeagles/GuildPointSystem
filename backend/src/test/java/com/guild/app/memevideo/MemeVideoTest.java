package com.guild.app.memevideo;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.memevideo.service.MemeVideoService;
import com.guild.app.memevideo.service.MemeVideoService.NewVideo;
import com.guild.app.memevideo.service.MemeVideoService.Video;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemeVideoTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired MemeVideoService service;
    @Autowired MemberRepository members;
    @MockitoBean WebSocketPublisher ws;
    String target;
    long targetId, adminId, memberId;

    long member(String nick, String role) {
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true)", nick, role);
        return jdbc.queryForObject("SELECT id FROM member WHERE nickname=?", Long.class, nick);
    }
    static String nick(String p) { return p + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    MemberPrincipal as(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }

    /** Terça 12:00 em Brasília: fora das janelas de CW. */
    static final Instant QUIET = Instant.parse("2026-11-17T15:00:00Z");

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM meme_video");
        target = nick("Tgt");
        targetId = member(target, "MEMBRO");
        adminId = member(nick("Adm"), "ADMINISTRADOR");
        memberId = member(nick("Mem"), "MEMBRO");
        service.useClock(Clock.fixed(QUIET, ZoneOffset.UTC));
    }

    String game(String character) throws Exception {
        return mvc.perform(get("/api/meme-videos/game").param("character", character))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test void parsesYoutubeLinks() {
        assertEquals(Optional.of(new Video("dQw4w9WgXcQ", 0)), MemeVideoService.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals(Optional.of(new Video("dQw4w9WgXcQ", 43)), MemeVideoService.parse("https://youtu.be/dQw4w9WgXcQ?t=43"));
        assertEquals(Optional.of(new Video("dQw4w9WgXcQ", 90)), MemeVideoService.parse("youtube.com/watch?list=x&v=dQw4w9WgXcQ&t=1m30s"));
        assertEquals(Optional.of(new Video("dQw4w9WgXcQ", 0)), MemeVideoService.parse("https://m.youtube.com/shorts/dQw4w9WgXcQ"));
        assertEquals(Optional.of(new Video("dQw4w9WgXcQ", 0)), MemeVideoService.parse("dQw4w9WgXcQ"));
        assertTrue(MemeVideoService.parse("https://evil.com/watch?v=dQw4w9WgXcQ").isEmpty());
        assertTrue(MemeVideoService.parse("https://youtube.com/watch?v=short").isEmpty());
        assertTrue(MemeVideoService.parse("").isEmpty());
    }

    @Test void onlyAdminSendsAndDllOpensOnce() throws Exception {
        assertFalse(service.view(as(memberId)).canSend());
        assertThrows(AppException.class, () -> service.send(new NewVideo(targetId, "dQw4w9WgXcQ"), as(memberId)));
        assertEquals(1, service.send(new NewVideo(targetId, "https://youtu.be/dQw4w9WgXcQ?t=5"), as(adminId)).recent().size());
        String body = game(target);
        assertTrue(body.matches("\\d+\tdQw4w9WgXcQ\t5\n"), body);
        assertEquals("", game(nick("Ninguem")));
        long id = Long.parseLong(body.split("\t")[0]);
        mvc.perform(post("/api/meme-videos/game/" + id + "/played").param("character", "outro")).andExpect(status().isNotFound());
        mvc.perform(post("/api/meme-videos/game/" + id + "/played").param("character", target)).andExpect(status().isNoContent());
        assertEquals("", game(target));
    }

    @Test void cooldownPerMember() {
        service.send(new NewVideo(targetId, "dQw4w9WgXcQ"), as(adminId));
        assertThrows(AppException.class, () -> service.send(new NewVideo(targetId, "dQw4w9WgXcQ"), as(adminId)));
        service.useClock(Clock.fixed(QUIET.plus(MemeVideoService.COOLDOWN).plusSeconds(1), ZoneOffset.UTC));
        service.send(new NewVideo(targetId, "dQw4w9WgXcQ"), as(adminId));
    }

    @Test void optOutBlocksAndClearsPending() throws Exception {
        service.send(new NewVideo(targetId, "dQw4w9WgXcQ"), as(adminId));
        service.setOptOut(as(targetId), true);
        assertEquals("", game(target));
        assertThrows(AppException.class, () -> service.send(new NewVideo(targetId, "dQw4w9WgXcQ"), as(adminId)));
    }

    @Test void waitsDuringRaidAndExpires() throws Exception {
        service.send(new NewVideo(targetId, "dQw4w9WgXcQ"), as(adminId));
        jdbc.update("INSERT INTO raid(name,leader_id) VALUES('R',?)", adminId);
        long raid = jdbc.queryForObject("SELECT max(id) FROM raid", Long.class);
        jdbc.update("INSERT INTO raid_member(raid_id,member_id,group_no) VALUES(?,?,1)", raid, targetId);
        assertEquals("", game(target));
        jdbc.update("DELETE FROM raid_member WHERE member_id=?", targetId);
        assertFalse(game(target).isEmpty());
        service.useClock(Clock.fixed(QUIET.plus(MemeVideoService.PENDING).plusSeconds(1), ZoneOffset.UTC));
        assertEquals("", game(target));
    }

    @Test void waitsDuringCw() {
        // 14:30 em Moscou (CW2), sem fim registrado.
        assertTrue(service.busy(targetId, Instant.parse("2026-11-17T11:30:00Z")));
        // 5 min antes da CW2.
        assertTrue(service.busy(targetId, Instant.parse("2026-11-17T10:55:00Z")));
        assertFalse(service.busy(targetId, QUIET));
    }
}
