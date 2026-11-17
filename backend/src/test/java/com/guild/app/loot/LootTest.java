package com.guild.app.loot;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.loot.service.LootService;
import com.guild.app.loot.service.LootService.Kind;
import com.guild.app.loot.service.LootService.Loot;
import com.guild.app.loot.service.LootService.Page;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LootTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired LootService service;
    @Autowired MemberRepository members;
    @MockitoBean WebSocketPublisher ws;
    String a, b, c;
    long aId;
    static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z");

    long member(String nick) {
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'MEMBRO','APROVADO',true)", nick);
        return jdbc.queryForObject("SELECT id FROM member WHERE nickname=?", Long.class, nick);
    }
    static String nick(String p) { return p + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    void at(Instant when) { service.useClock(Clock.fixed(when, ZoneOffset.UTC)); }
    int count() { return jdbc.queryForObject("SELECT count(*) FROM member_loot WHERE member_id=?", Integer.class, aId); }

    @BeforeEach void setup() {
        a = nick("A");
        aId = member(a);
        b = nick("B");
        member(b);
        c = nick("C");
        member(c);
        at(NOW);
    }

    @Test void parsesTheThreeKinds() {
        assertEquals(Optional.of(new Loot("eat", Kind.DROP, "KnightWalker Arm Guard<R-type>", 1)),
            LootService.parse("[300] : eat has acquired KnightWalker Arm Guard<R-type>."));
        assertEquals(Optional.of(new Loot("Juninho", Kind.PARTY, "Type-C Armor Box 40(45)", 1)),
            LootService.parse("[557] : Juninho has obtained 1 Type-C Armor Box 40(45)."));
        assertEquals(Optional.of(new Loot("Coxinha", Kind.PARTY, "Citadel Seal", 12)),
            LootService.parse("Coxinha has obtained 12 Citadel Seal."));
        assertEquals(Optional.of(new Loot("meow", Kind.REWARD, "BD 50-65(55) Reward Box(st 1.2)", 1)),
            LootService.parse("[379] : Compensation item for meow - BD 50-65(55) Reward Box(st 1.2)*"));
        assertTrue(LootService.parse("[55] : Maximum temporary point thst can be obtained from PC cafe is 675 point.").isEmpty());
        assertTrue(LootService.parse("[48] : Gassakerr have logged in.").isEmpty());
        assertTrue(LootService.parse("[1Knot]AAFF : x has acquired y.").isEmpty());
    }

    @Test void keepsOnlyMembersAndMergesSameLineFromOthers() throws Exception {
        String line = "[1] : " + a + " has acquired Smart Intense Hora Staff.\n[2] : stranger has acquired Ether Wing.\n";
        mvc.perform(post("/api/loot/game").param("character", b).content(line))
            .andExpect(status().isOk()).andExpect(content().string("1"));
        // Outro membro viu o mesmo anúncio: não duplica.
        mvc.perform(post("/api/loot/game").param("character", c).content(line))
            .andExpect(status().isOk()).andExpect(content().string("0"));
        assertEquals(1, count());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM member_loot_report r JOIN member_loot l ON l.id=r.loot_id WHERE l.member_id=?",
            Integer.class, aId));
    }

    @Test void sameReporterTwiceIsTwoDrops() {
        String line = a + " has acquired Endurance Intense Azzle Rayment Boots.";
        assertEquals(2, service.ingest(b, line + "\n" + line));
        // O outro membro viu os dois: junta com os dois.
        assertEquals(0, service.ingest(c, line + "\n" + line));
        assertEquals(2, count());
        // Depois da janela, é drop novo.
        at(NOW.plus(LootService.MERGE).plusSeconds(1));
        assertEquals(1, service.ingest(c, line));
        assertEquals(3, count());
    }

    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void pagesNewestFirst() {
        jdbc.update("DELETE FROM member_loot");
        StringBuilder lines = new StringBuilder();
        for (int i = 1; i <= 7; i++) lines.append(a).append(" has acquired Item ").append(i).append(".\n");
        assertEquals(7, service.ingest(b, lines.toString()));
        String adminNick = nick("Adm");
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'ADMINISTRADOR','APROVADO',true)", adminNick);
        long admin = jdbc.queryForObject("SELECT id FROM member WHERE nickname=?", Long.class, adminNick);
        MemberPrincipal p = new MemberPrincipal(members.findById(admin).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        Page first = service.query(a, null, null, 7, 0, 3);
        assertEquals(7, first.total());
        assertEquals(3, first.items().size());
        assertEquals("Item 7", first.items().get(0).item());  // mesmo horário: o id maior vem primeiro
        Page last = service.query(a, null, "drop", 7, 2, 3);
        assertEquals(1, last.items().size());
        assertEquals("Item 1", last.items().get(0).item());
        assertEquals(0, service.query(a, "nada", null, 7, 0, 3).total());
    }

    @Test void rejectsUnknownReporter() {
        AppException e = assertThrows(AppException.class, () -> service.ingest("ninguem" + UUID.randomUUID(), a + " has acquired X."));
        assertEquals(403, e.getStatus().value());
    }
}
