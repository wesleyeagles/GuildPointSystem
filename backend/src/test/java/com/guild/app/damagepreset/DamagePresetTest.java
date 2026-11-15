package com.guild.app.damagepreset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guild.app.common.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DamagePresetTest {

    @Autowired DamagePresetService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    long a, b;

    long member() {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'MEMBRO','APROVADO',true) RETURNING id",
                Long.class, "Dp" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    ObjectNode state(int keen) { return mapper.createObjectNode().put("weapon", "iwswa65").put("keen", keen); }

    @BeforeEach void setup() { a = member(); b = member(); }

    @Test void eachMemberSeesOnlyTheirOwnAndSameNameReplaces() {
        service.save(a, "Keen 7", state(7));
        service.save(a, "Sem keen", state(0));
        service.save(b, "Do B", state(1));
        var list = service.save(a, "  keen 7 ", state(6));     // mesmo nome (sem maiúscula/espaço) substitui
        assertEquals(List.of("keen 7", "Sem keen"), list.stream().map(DamagePresetService.Preset::name).toList());
        assertEquals(6, list.get(0).state().get("keen").asInt());
        assertEquals(List.of("Do B"), service.list(b).stream().map(DamagePresetService.Preset::name).toList());
        assertEquals(1, service.delete(a, "SEM KEEN").size());
        assertEquals(1, service.list(b).size());                // excluir do A não mexe no B
    }

    @Test void importKeepsWhatIsAlreadyOnTheAccount() {
        service.save(a, "Keen 7", state(7));
        var list = service.importAll(a, List.of(
                new DamagePresetService.Preset("keen 7", state(1), null),
                new DamagePresetService.Preset("Novo", state(2), null)));
        assertEquals(2, list.size());
        assertEquals(7, list.get(0).state().get("keen").asInt());
    }

    @Test void rejectsBlankNamesNonObjectsAndTooMany() {
        assertThrows(AppException.class, () -> service.save(a, "  ", state(0)));
        assertThrows(AppException.class, () -> service.save(a, "x", mapper.createArrayNode()));
        for (int i = 0; i < DamagePresetService.MAX_PRESETS; i++) service.save(a, "P" + i, state(0));
        assertThrows(AppException.class, () -> service.save(a, "Mais um", state(0)));
        service.save(a, "P1", state(3));                         // substituir ainda pode
    }
}
