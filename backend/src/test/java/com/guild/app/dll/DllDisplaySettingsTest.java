package com.guild.app.dll;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.dll.service.DllDisplaySettingsService;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DllDisplaySettingsTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @MockitoBean WebSocketPublisher ws;

    MemberPrincipal member(String role) {
        long id = jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete,points) "
            + "VALUES(?,?,'APROVADO',true,0) RETURNING id", Long.class, "display-" + UUID.randomUUID(), role);
        return new MemberPrincipal(members.findById(id).orElseThrow());
    }

    @Test void adminSetsTheArrowSizesAndTheShoutHeadersCarryThem() throws Exception {
        var admin = member("ADMINISTRADOR");
        mvc.perform(put("/api/dll/display").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"partyArrowScale\":180,\"guildArrowScale\":250,\"arrowDistance\":60,\"markerArrowScale\":220,\"markerColor\":\"#33cc66\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.partyArrowScale").value(180))
            .andExpect(jsonPath("$.guildArrowScale").value(250))
            .andExpect(jsonPath("$.arrowDistance").value(60))
            .andExpect(jsonPath("$.markerArrowScale").value(220))
            .andExpect(jsonPath("$.markerColor").value("33CC66"))
            .andExpect(jsonPath("$.updatedBy").value(admin.getNickname()));
        mvc.perform(get("/api/dll/display").with(user(admin)))
            .andExpect(jsonPath("$.partyArrowScale").value(180))
            .andExpect(jsonPath("$.guildArrowScale").value(250));
        mvc.perform(get("/api/shout/game").param("character", "ninguem"))
            .andExpect(header().string("X-Cerberus-Party-Arrow", "180"))
            .andExpect(header().string("X-Cerberus-Guild-Arrow", "250"))
            .andExpect(header().string("X-Cerberus-Arrow-Distance", "60"))
            .andExpect(header().string("X-Cerberus-Marker-Arrow", "220"))
            .andExpect(header().string("X-Cerberus-Marker-Color", "33CC66"));
    }

    @Test void outOfRangeIsRejectedAndMembersCannotChangeIt() throws Exception {
        var admin = member("ADMINISTRADOR");
        mvc.perform(put("/api/dll/display").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"partyArrowScale\":100,\"guildArrowScale\":" + (DllDisplaySettingsService.MAX_SCALE + 1) + "}"))
            .andExpect(status().isBadRequest());
        var plain = member("MEMBRO");
        mvc.perform(put("/api/dll/display").with(user(plain)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"partyArrowScale\":150,\"guildArrowScale\":100}"))
            .andExpect(status().isForbidden());
    }
}
