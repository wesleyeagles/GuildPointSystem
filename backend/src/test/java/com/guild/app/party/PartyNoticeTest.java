package com.guild.app.party;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.party.service.PartyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PartyNoticeTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired PartyService service;

    MemberPrincipal member(String role) {
        long id = jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete,points) "
            + "VALUES(?,?,'APROVADO',true,0) RETURNING id", Long.class, "notice-" + UUID.randomUUID(), role);
        return new MemberPrincipal(members.findById(id).orElseThrow());
    }

    @Test void adminSavesAndRemovesTheNoticeAndSnapshotsIncludeIt() throws Exception {
        var admin = member("ADMINISTRADOR");
        mvc.perform(put("/api/parties/notice").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"  Up Z nos níveis 48  \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.notice").value("Up Z nos níveis 48"));
        assertEquals("Up Z nos níveis 48", service.getPublicSnapshot().notice());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE type='PARTY_QUEUE_UPDATED' "
            + "AND actor_id=? AND payload->>'action'='NOTICE_UPDATED'", Integer.class, admin.getId()));
        mvc.perform(put("/api/parties/notice").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.notice").value(""));
        assertEquals("", service.getPublicSnapshot().notice());
    }

    @Test void rejectsMembersAndInvalidTextBeforeChangingTheNotice() throws Exception {
        mvc.perform(put("/api/parties/notice").with(user(member("MEMBRO"))).contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"Sem permissão\"}")).andExpect(status().isForbidden());
        var admin = member("ADMINISTRADOR");
        mvc.perform(put("/api/parties/notice").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"" + "x".repeat(1001) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/parties/notice").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isBadRequest());
    }
}
