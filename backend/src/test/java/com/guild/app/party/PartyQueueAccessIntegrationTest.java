package com.guild.app.party;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.party.dto.*;
import com.guild.app.party.service.PartyService;
import com.guild.app.party.service.PartyQueueAccessService;
import com.guild.app.common.enums.PartyMap;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PartyQueueAccessIntegrationTest {
    @Autowired PartyService parties;
    @Autowired PartyQueueAccessService gate;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    long admin,player,other,moderator;
    long member(String role) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id",Long.class,"Gate "+UUID.randomUUID(),role);
    }
    MemberPrincipal principal(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }
    UsernamePasswordAuthenticationToken auth(long id) { var p=principal(id);return new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()); }
    void at(String time) { ReflectionTestUtils.setField(gate,"clock",Clock.fixed(Instant.parse(time),ZoneOffset.UTC)); }
    @BeforeEach void setup() {
        admin=member("ADMINISTRADOR");player=member("MEMBRO");other=member("MEMBRO");moderator=member("MODERADOR");
        jdbc.update("UPDATE party_queue_access SET override_open=NULL,override_until=NULL WHERE id=1");
        at("2026-10-31T15:00:00Z");
    }
    @AfterEach void resetClock() { ReflectionTestUtils.setField(gate,"clock",Clock.systemUTC()); }

    @Test void closedHoursBlockBothEntryRoutesAndOnlyAdminsCanOpen() throws Exception {
        mvc.perform(get("/api/parties/board").with(authentication(auth(player))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.queueAccess.open").value(false));
        mvc.perform(put("/api/parties/lfg").with(authentication(auth(player))).contentType("application/json").content("{}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/parties/lfg/"+player).with(authentication(auth(admin)))).andExpect(status().isConflict());
        for(long actor:new long[]{player,moderator})
            mvc.perform(put("/api/parties/lfg/access").with(authentication(auth(actor))).contentType("application/json").content("{\"open\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/parties/lfg/access").with(anonymous()).contentType("application/json").content("{\"open\":true}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/parties/lfg/access").with(authentication(auth(admin))).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/parties/lfg/access").with(authentication(auth(admin))).contentType("application/json").content("{\"open\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.queueAccess.manual").value(true));
        parties.upsertLfg(new UpsertLfgRequest(null),principal(player));
        parties.addMemberToLfg(other,principal(admin));
        assertNotNull(parties.getBoard(principal(player)).my().lfg());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE actor_id=? AND type='PARTY_QUEUE_UPDATED' AND payload->>'action'='QUEUE_OPENED'",Integer.class,admin));
    }
    @Test void manualClosingPreservesQueueTimesAndAllowsNotesExitAndAutomaticPartyReturn() {
        at("2026-10-31T21:00:00Z");
        var first=parties.upsertLfg(new UpsertLfgRequest(null),principal(player)).my().lfg();
        long party=parties.createParty(new CreatePartyRequest(PartyMap.ELAN,"gate test"),principal(other)).my().partyId();
        parties.updateQueueAccess(new UpdatePartyQueueAccessRequest(false),principal(admin));
        var updated=parties.upsertLfg(new UpsertLfgRequest("nota"),principal(player)).my().lfg();
        assertEquals(first.createdAt(),updated.createdAt());assertEquals(first.queueOrder(),updated.queueOrder());
        parties.leave(party,principal(other));
        assertNotNull(parties.getBoard(principal(other)).my().lfg());
        parties.leaveLfg(principal(player));
        assertThrows(RuntimeException.class,()->parties.upsertLfg(new UpsertLfgRequest(null),principal(player)));
        at("2026-11-01T09:00:00Z");
        assertFalse(gate.current().open());assertFalse(gate.current().manual());
        at("2026-11-01T21:00:00Z");assertTrue(gate.current().open());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE actor_id=? AND payload->>'action'='QUEUE_CLOSED'",Integer.class,admin));
    }
    @Test void manualOpeningPersistsInTheDatabaseAndExpiresAtEighteen() {
        parties.updateQueueAccess(new UpdatePartyQueueAccessRequest(true),principal(admin));
        assertEquals(Instant.parse("2026-10-31T21:00:00Z"),jdbc.queryForObject("SELECT override_until FROM party_queue_access WHERE id=1",java.sql.Timestamp.class).toInstant());
        at("2026-10-31T21:00:00Z");assertTrue(gate.current().open());assertFalse(gate.current().manual());
        at("2026-11-01T09:00:00Z");assertFalse(gate.current().open());
        assertEquals(gate.current(),parties.getPublicSnapshot().queueAccess());
    }
}
