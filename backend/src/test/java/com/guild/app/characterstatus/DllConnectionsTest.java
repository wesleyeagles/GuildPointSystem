package com.guild.app.characterstatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.app.auth.security.JwtService;
import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.characterstatus.dto.CharacterStatusIngestRequest;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "guild.sync.enforce-from=2099-01-01T00:00:00Z")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DllConnectionsTest {
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired JwtService jwt;
    @MockitoBean WebSocketPublisher ws;
    String name() { return "Ip" + UUID.randomUUID().toString().replace("-", "").substring(0, 9); }
    long member(String nick, String role) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id", Long.class, nick, role);
    }
    String token(long id) {return "Bearer " + jwt.generateToken(new MemberPrincipal(members.findById(id).orElseThrow()));}
    void upload(String nick, long gid, String ip) throws Exception {
        var body = new CharacterStatusIngestRequest(gid,nick,50,1f,10,10,1,1,1,1);
        mvc.perform(post("/api/character-status/ingest").with(r -> {r.setRemoteAddr(ip);return r;})
                .header("User-Agent","CerberusBags/1.52.1").contentType("application/json").content(json.writeValueAsBytes(body)))
                .andExpect(status().isNoContent());
    }
    @Test void recordsSourceAndGroupsDistinctRecentCharactersWithoutLeakingIps() throws Exception {
        String a=name(),b=name(),c=name();long aId=member(a,"MEMBRO"),bId=member(b,"MEMBRO");member(c,"MEMBRO");
        String admin=token(member(name(),"ADMINISTRADOR"));
        upload(a,9,"203.0.113.7");upload(a,10,"203.0.113.7");upload(b,9,"203.0.113.7");upload(c,9,"203.0.113.8");
        var result=mvc.perform(get("/api/dll/connections").header("Authorization",admin)).andExpect(status().isOk()).andReturn();
        var rows=json.readTree(result.getResponse().getContentAsString());assertEquals(3,rows.size());
        for(var row:rows) assertEquals(row.get("character").asText().equals(c)?1:2,row.get("sameIpCharacters").asInt());
        jdbc.update("UPDATE character_status SET updated_at=now()-interval '4 minutes' WHERE character_name=?",c);
        result=mvc.perform(get("/api/dll/connections").header("Authorization",admin)).andExpect(status().isOk()).andReturn();
        assertEquals(2,json.readTree(result.getResponse().getContentAsString()).size());
        upload(b,9,"198.51.100.6");
        result=mvc.perform(get("/api/dll/connections").header("Authorization",admin)).andExpect(status().isOk()).andReturn();
        for(var row:json.readTree(result.getResponse().getContentAsString()))assertEquals(1,row.get("sameIpCharacters").asInt());
        String publicBody=mvc.perform(get("/api/character-status/ranking").header("Authorization",token(aId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(publicBody.contains("203.0.113.7"));assertFalse(publicBody.contains("uploadIp"));
        verify(ws,atLeastOnce()).publishCharacterStatus(argThat(s -> s instanceof com.guild.app.characterstatus.dto.CharacterStatusView v && v.characterName().equals(a)));
        assertEquals("198.51.100.6",jdbc.queryForObject("SELECT upload_ip FROM character_status WHERE member_id=? AND gid=9",String.class,bId));
    }
    @Test void sharedCharacterRegistrationsAreNotCountedAsTwoCharacters() throws Exception {
        String nick=name();member(nick,"MEMBRO");member(nick.toUpperCase(),"MEMBRO");
        upload(nick,9,"203.0.113.7");
        var response=mvc.perform(get("/api/dll/connections").header("Authorization",token(member(name(),"ADMINISTRADOR"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var rows=json.readTree(response);assertEquals(1,rows.size());assertEquals(1,rows.get(0).get("sameIpCharacters").asInt());
    }
    @Test void rejectsNonAdminsAndKeepsUnknownIpsOutOfTheReport() throws Exception {
        String nick=name();long id=member(nick,"MEMBRO");upload(nick,9,"203.0.113.7");upload(nick,9,"127.0.0.1");
        entityManager.flush();
        assertNull(jdbc.queryForObject("SELECT upload_ip FROM character_status WHERE member_id=? AND gid=9",String.class,id));
        mvc.perform(get("/api/dll/connections")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dll/connections").header("Authorization",token(id))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dll/connections").header("Authorization",token(member(name(),"MODERADOR")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dll/connections").header("Authorization",token(member(name(),"ADMINISTRADOR"))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
