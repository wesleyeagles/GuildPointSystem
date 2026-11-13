package com.guild.app.log;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.log.dto.AuditLogResponse;
import com.guild.app.log.dto.LogFilter;
import com.guild.app.log.service.LogService;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Aba Logs: filtros por tipo, evento e player; equipamento sincronizado pelo jogo nunca aparece. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LogServiceFilterTest {
    @Autowired LogService service;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WebSocketPublisher ws;

    String tag;
    long admin, alice, bob;

    long member(String role) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id",
                Long.class, tag + UUID.randomUUID().toString().substring(0, 4), role);
    }
    void log(AuditLogType type, Long actor, Long target, String payload) {
        jdbc.update("INSERT INTO audit_log(type,actor_id,target_id,payload,created_at) VALUES(?,?,?,?::jsonb,now())",
                type.name(), actor, target, payload);
    }
    List<AuditLogResponse> list(AuditLogType type, Long memberId, String objective) {
        return service.list(new LogFilter(type, memberId, objective), PageRequest.of(0, 500)).getContent();
    }

    @BeforeEach void setup() {
        tag = "Log" + UUID.randomUUID().toString().substring(0, 4);
        admin = member("ADMINISTRADOR");
        alice = member("MEMBRO");
        bob = member("MEMBRO");
        log(AuditLogType.EVENT_CLAIMED, alice, alice, "{\"objectiveName\":\"" + tag + " Boss\"}");
        log(AuditLogType.EVENT_CLAIMED, bob, bob, "{\"objectiveName\":\"" + tag + " Boss\"}");
        log(AuditLogType.EVENT_CLAIMED, bob, bob, "{\"objectiveName\":\"" + tag + " PB\"}");
        log(AuditLogType.POINTS_ADJUSTED, admin, alice, "{\"amount\":5}");
        log(AuditLogType.EQUIPMENT_CHANGED, alice, alice, "{\"action\":\"CLIENT_SYNC\",\"changes\":[]}");
        log(AuditLogType.EQUIPMENT_CHANGED, admin, alice, "{\"action\":\"CREATE\"}");
        var p = new MemberPrincipal(members.findById(admin).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void filtersByEvent() {
        var logs = list(null, null, tag + " Boss");
        assertEquals(2, logs.size());
        assertTrue(logs.stream().allMatch(l -> l.type() == AuditLogType.EVENT_CLAIMED));
        assertTrue(service.objectiveNames().containsAll(List.of(tag + " Boss", tag + " PB")));
    }

    @Test void filtersByPlayerAsActorOrTarget() {
        var logs = list(null, alice, null);
        // claim + ajuste recebido + equipamento lançado pelo admin; o sync do jogo fica de fora
        assertEquals(3, logs.size());
        assertEquals(1, list(AuditLogType.POINTS_ADJUSTED, alice, null).size());
        assertEquals(1, list(AuditLogType.EVENT_CLAIMED, bob, tag + " PB").size());
    }

    @Test void gameSyncedEquipmentNeverShowsUp() {
        assertTrue(list(null, null, null).stream().noneMatch(l -> "CLIENT_SYNC".equals(l.payload().get("action"))));
        assertEquals(1, list(AuditLogType.EQUIPMENT_CHANGED, alice, null).size());
    }
}
