package com.guild.app.equipment;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.*;
import com.guild.app.equipment.dto.EquipmentDtos.*;
import com.guild.app.equipment.service.EquipmentService;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RelicClientSyncTest {
    @Autowired EquipmentService service;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean WebSocketPublisher ws;
    long adminId,memberId;
    String nickname;

    long member(Role role,String name) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id",
            Long.class,name,role.name());
    }
    void authenticate(long id) {
        var principal=new MemberPrincipal(members.findById(id).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }
    @BeforeEach void setup() {
        adminId=member(Role.ADMINISTRADOR,"Relic Admin "+UUID.randomUUID());
        nickname="RelicChar"+UUID.randomUUID().toString().substring(0,8);
        memberId=member(Role.MEMBRO,nickname);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    ClientRelics report(int... pairs) {
        var list=new ArrayList<ReportedRelic>();
        for(int i=0;i<pairs.length;i+=2) list.add(new ReportedRelic(pairs[i],pairs[i+1]));
        return new ClientRelics(nickname.toLowerCase(),list);
    }
    MemberRelics saved() { authenticate(adminId); var r=service.relics(memberId); SecurityContextHolder.clearContext(); return r; }
    int audits() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE payload->>'action'='CLIENT_SYNC' AND target_id=?",Integer.class,memberId);
    }

    @Test void replacesTheWholeSetWithUpgradesAndLogsOnlyChanges() {
        assertEquals(3,service.syncRelicsFromClient(report(2,1,3,0,111,0)).get("changed"));
        var first=saved();
        assertEquals(List.of(new RelicEntry(2,1),new RelicEntry(3,0),new RelicEntry(111,0)),first.relics());
        assertNotNull(first.syncedAt());
        assertEquals(1,audits());
        verify(ws,times(1)).publishEquipmentChanged();

        // A mesma leitura não gera log nem aviso, só atualiza a hora da sincronização.
        assertEquals(0,service.syncRelicsFromClient(report(2,1,3,0,111,0)).get("changed"));
        assertEquals(1,audits());
        verify(ws,times(1)).publishEquipmentChanged();

        // Upgrade novo, relic nova e relic que sumiu.
        assertEquals(3,service.syncRelicsFromClient(report(2,2,111,0,98,0)).get("changed"));
        assertEquals(List.of(new RelicEntry(2,2),new RelicEntry(98,0),new RelicEntry(111,0)),saved().relics());
        assertEquals(2,audits());

        // Conjunto vazio é uma leitura válida (personagem sem relics).
        assertEquals(3,service.syncRelicsFromClient(report()).get("changed"));
        assertEquals(List.of(),saved().relics());
    }
    @Test void gameSyncLogsStayHiddenFromTheLogsTab() {
        service.syncRelicsFromClient(report(7,0));
        assertEquals(1,audits());
        authenticate(adminId);
        var visible=jdbc.queryForObject("""
            SELECT count(*) FROM audit_log WHERE target_id=? AND type='EQUIPMENT_CHANGED' AND payload->>'action'<>'CLIENT_SYNC'
            """,Integer.class,memberId);
        assertEquals(0,visible);
    }
    @Test void ingestIsPublicButValidatesPayload() throws Exception {
        mvc.perform(post("/api/relic-status/ingest").contentType("application/json")
            .content("{\"character\":\""+nickname+"\",\"relics\":[{\"id\":10,\"upgrade\":0}]}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(1));
        for(String bad:List.of("[{\"id\":120,\"upgrade\":0}]","[{\"id\":-1,\"upgrade\":0}]","[{\"id\":1,\"upgrade\":4}]",
                "[{\"id\":1,\"upgrade\":0},{\"id\":1,\"upgrade\":1}]"))
            mvc.perform(post("/api/relic-status/ingest").contentType("application/json")
                .content("{\"character\":\""+nickname+"\",\"relics\":"+bad+"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/relic-status/ingest").contentType("application/json")
            .content("{\"character\":\"NoSuchChar"+UUID.randomUUID().toString().substring(0,6)+"\",\"relics\":[]}")).andExpect(status().isNotFound());
        assertEquals(List.of(new RelicEntry(10,0)),saved().relics());
    }
    @Test void manualAdminToggleKeepsTheSyncedUpgrade() {
        service.syncRelicsFromClient(report(2,1));
        authenticate(adminId);
        assertEquals(List.of(new RelicEntry(2,1)),service.setRelic(memberId,2,new RelicState(true)).relics());
        assertEquals(List.of(new RelicEntry(2,1),new RelicEntry(5,0)),service.setRelic(memberId,5,new RelicState(true)).relics());
    }
}
