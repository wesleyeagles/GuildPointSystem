package com.guild.app.pbauto;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.pbauto.service.AutoPointRuleService;
import com.guild.app.pbauto.service.AutoPointRuleService.RuleRequest;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AutoPointRuleServiceTest {
    @Autowired AutoPointRuleService service;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WebSocketPublisher ws;

    long member(String role) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id",
                Long.class, "Ap" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), role);
    }
    void loginAs(long id) {
        var p = new MemberPrincipal(members.findById(id).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    static RuleRequest elan(List<Integer> days) {
        return new RuleRequest("PB Elan", true, days, List.of(10, 5), "PB Elan às 14:00", null,
                true, "13:52", "Cora HQ", List.of("Cora Outpost"), 2L,
                true, "13:52", "14:20", "Elan", List.of(), "PB Elan");
    }

    @Test void leaderCreatesEditsAndDeletes_memberCannot() {
        loginAs(member("MEMBRO"));
        assertEquals(403, assertThrows(AppException.class, () -> service.create(elan(List.of(1, 2, 4, 6)))).getStatus().value());

        // Só existe um Líder (idx_single_leader): usa o que já existe, ou cria.
        loginAs(jdbc.queryForList("SELECT id FROM member WHERE role='LIDER'", Long.class).stream().findFirst()
                .orElseGet(() -> member("LIDER")));
        var created = service.create(elan(List.of(1, 2, 4, 6)));
        assertEquals("R" + created.id(), created.code());
        assertEquals(List.of(1, 2, 4, 6), created.weekdays());
        assertEquals("13:52", created.rgAt());
        assertEquals(List.of(), created.presenceRegions());
        assertTrue(created.objectiveMissing());                     // "PB Elan" ainda não existe na aba Objetivos

        var edited = service.update(created.id(), elan(List.of(2, 6)));
        assertEquals(List.of(2, 6), edited.weekdays());
        assertTrue(service.list().stream().anyMatch(r -> r.id() == created.id() && r.weekdays().equals(List.of(2, 6))));
        // Os dois PBs que já eram automáticos vieram da migration.
        assertTrue(service.list().stream().anyMatch(r -> r.code().equals("COLONIA_02")));

        service.delete(created.id());
        assertTrue(service.list().stream().noneMatch(r -> r.id() == created.id()));
    }

    @Test void rejectsInvalidForms() {
        loginAs(member("ADMINISTRADOR"));
        assertThrows(AppException.class, () -> service.create(elan(List.of())));                     // sem dia
        var noSteps = new RuleRequest("X", true, List.of(1), List.of(), null, null,
                false, null, null, null, null, false, null, null, null, null, null);
        assertThrows(AppException.class, () -> service.create(noSteps));                              // nem RG nem presença
        var badTime = new RuleRequest("X", true, List.of(1), List.of(), null, null,
                true, "25:00", "Cora HQ", List.of(), 2L, false, null, null, null, null, null);
        assertThrows(AppException.class, () -> service.create(badTime));
        var shoutWithoutText = new RuleRequest("X", true, List.of(1), List.of(5), "", null,
                true, "10:00", "Cora HQ", List.of(), 2L, false, null, null, null, null, null);
        assertThrows(AppException.class, () -> service.create(shoutWithoutText));
    }
}
