package com.guild.app.raid;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.party.service.PartyGameInviteService;
import com.guild.app.common.enums.Role;
import com.guild.app.raid.service.RaidAccessService;
import com.guild.app.raid.service.RaidService;
import com.guild.app.raid.service.RaidService.RaidMemberView;
import com.guild.app.raid.service.RaidService.RaidView;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.lang.reflect.Field;
import java.time.*;
import java.util.*;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RaidTest {
    @Autowired RaidService service;
    @Autowired RaidAccessService access;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean WebSocketPublisher ws;
    long leaderId, adminId, outsiderId, pendingId;
    String leaderNick;
    Instant now = Instant.parse("2026-11-02T13:00:00Z");

    long member(String nick, String role, String status) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true) RETURNING id",
            Long.class, nick, role, status);
    }
    long member() { return member(nick("M"), "MEMBRO", "APROVADO"); }
    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    MemberPrincipal as(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }
    String nickOf(long id) { return members.findById(id).orElseThrow().getNickname(); }
    void at(Instant instant) throws Exception {
        Field f = RaidService.class.getDeclaredField("clock"); f.setAccessible(true);
        f.set(org.springframework.test.util.AopTestUtils.getTargetObject(service), Clock.fixed(instant, ZoneOffset.UTC));
    }
    List<Long> many(int n) { return LongStream.range(0, n).map(i -> member()).boxed().toList(); }
    RaidView raid(long id) { return service.list(as(leaderId)).stream().filter(r -> r.id() == id).findFirst().orElseThrow(); }
    Map<Integer, List<RaidMemberView>> groups(RaidView view) {
        var out = new TreeMap<Integer, List<RaidMemberView>>();
        for (var m : view.members()) out.computeIfAbsent(m.group(), g -> new ArrayList<>()).add(m);
        return out;
    }

    @BeforeEach void setup() throws Exception {
        leaderNick = nick("Lead");
        leaderId = member(leaderNick, "MEMBRO", "APROVADO");
        adminId = member(nick("Adm"), "ADMINISTRADOR", "APROVADO");
        outsiderId = member();
        pendingId = member(nick("Pend"), "MEMBRO", "PENDENTE");
        at(now);
    }
    @AfterEach void resetClock() throws Exception { at(Instant.now()); }

    @Test void raidHasNoMemberLimitAndFillsGroupsOfEight() {
        var raid = service.create("Raid PB", as(leaderId));
        var view = service.addMembers(raid.id(), many(20), null, false, as(leaderId));
        assertEquals(21, view.members().size());
        var groups = groups(view);
        assertEquals(List.of(8, 8, 5), groups.values().stream().map(List::size).toList());
        assertTrue(groups.get(1).getFirst().raidLeader() && groups.get(1).getFirst().groupLeader());
        for (var g : groups.values()) assertEquals(1, g.stream().filter(RaidMemberView::groupLeader).count());
        verify(ws, atLeastOnce()).publishRaids();
    }

    @Test void onlyLeaderOrAdministratorManages() {
        var raid = service.create("Raid", as(leaderId));
        long other = member();
        assertThrows(AppException.class, () -> service.addMembers(raid.id(), List.of(other), null, false, as(outsiderId)));
        assertEquals(2, service.addMembers(raid.id(), List.of(other), null, false, as(adminId)).members().size());
        assertFalse(service.list(as(outsiderId)).getFirst().canManage());
        assertTrue(raid(raid.id()).canManage());
    }

    @Test void memberCanBeInOnlyOneRaidAndPendingMembersAreRefused() {
        var a = service.create("A", as(leaderId));
        var b = service.create("B", as(outsiderId));
        long x = member();
        service.addMembers(a.id(), List.of(x), null, false, as(leaderId));
        var busy = assertThrows(AppException.class, () -> service.addMembers(b.id(), List.of(x), null, false, as(outsiderId)));
        assertEquals(HttpStatus.CONFLICT, busy.getStatus());
        assertThrows(AppException.class, () -> service.addMembers(a.id(), List.of(pendingId), null, false, as(leaderId)));
        assertThrows(AppException.class, () -> service.create("C", as(x)));
    }

    @Test void groupsHoldAtMostEightOnAddAndMove() {
        var raid = service.create("Raid", as(leaderId));
        service.addMembers(raid.id(), many(7), 1, false, as(leaderId));
        long extra = member();
        assertThrows(AppException.class, () -> service.addMembers(raid.id(), List.of(extra), 1, false, as(leaderId)));
        service.addMembers(raid.id(), List.of(extra), 2, false, as(leaderId));
        assertThrows(AppException.class, () -> service.moveMember(raid.id(), extra, 1, as(leaderId)));
        var moved = service.moveMember(raid.id(), extra, 5, as(leaderId));
        assertEquals(5, moved.members().stream().filter(m -> m.id() == extra).findFirst().orElseThrow().group());
    }

    @Test void groupLeaderCanBeChosenAndResetsOnMove() {
        var raid = service.create("Raid", as(leaderId));
        var ids = many(3);
        service.addMembers(raid.id(), ids, 2, false, as(leaderId));
        assertEquals(ids.get(0), groups(raid(raid.id())).get(2).getFirst().id());
        var view = service.setGroupLeader(raid.id(), ids.get(2), as(leaderId));
        assertEquals(ids.get(2), groups(view).get(2).getFirst().id());
        view = service.moveMember(raid.id(), ids.get(2), 1, as(leaderId));
        assertEquals(leaderId, groups(view).get(1).getFirst().id());
        assertEquals(ids.get(0), groups(view).get(2).getFirst().id());
    }

    @Test void leaderLeavingPassesLeadershipAndLastOneOutEndsRaid() {
        var raid = service.create("Raid", as(leaderId));
        long next = member();
        service.addMembers(raid.id(), List.of(next), null, false, as(leaderId));
        assertThrows(AppException.class, () -> service.removeMember(raid.id(), leaderId, as(next)));
        service.removeMember(raid.id(), leaderId, as(leaderId));
        assertEquals(next, raid(raid.id()).leaderId());
        service.removeMember(raid.id(), next, as(next));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM raid WHERE id=?", Integer.class, raid.id()));
    }

    @Test void callInGameCreatesOneInviteForEachGroupLeaderDeliveredThroughThePartyRoute() throws Exception {
        var raid = service.create("Raid", as(leaderId));
        var ids = service.addMembers(raid.id(), many(10), null, false, as(leaderId)).members();
        service.addMembers(raid.id(), List.of(member()), 4, false, as(leaderId)); // subgrupo só com o líder: ninguém a chamar
        var invites = service.callInGame(raid.id(), as(leaderId));
        assertEquals(List.of(1, 2), invites.stream().map(RaidService.GroupInviteView::group).toList());
        assertEquals(leaderNick, invites.get(0).leader());
        assertEquals(7, invites.get(0).nicks().size());
        assertEquals(2, invites.get(1).nicks().size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE type='RAID_GAME_INVITE' AND actor_id=?", Integer.class, leaderId));

        String group2Leader = invites.get(1).leader();
        assertNotEquals(leaderNick, group2Leader);
        mvc.perform(get("/api/party-invites/pending").param("character", leaderNick))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(invites.get(0).id().toString()))
            .andExpect(jsonPath("$.nicks.length()").value(7));
        mvc.perform(get("/api/party-invites/pending").param("character", leaderNick)).andExpect(status().isNoContent());
        mvc.perform(post("/api/party-invites/{id}/done", invites.get(0).id()).param("character", leaderNick))
            .andExpect(status().isNoContent());
        assertEquals("COMPLETE", service.invites(raid.id(), as(outsiderId)).getFirst().state());
        verify(ws, times(7)).publishPartyMemberNotice(anyLong(), argThat(n -> n instanceof PartyGameInviteService.InviteNotice m
            && m.type().equals("PARTY_GAME_INVITE") && m.inviteId().equals(invites.get(0).id())));

        assertEquals(invites.get(1).id(), service.pending(group2Leader).orElseThrow().id());
        assertTrue(ids.size() > 8);
    }

    @Test void waitingInviteAndCooldownBlockRepeatedCalls() throws Exception {
        var raid = service.create("Raid", as(leaderId));
        service.addMembers(raid.id(), many(2), null, false, as(leaderId));
        service.callInGame(raid.id(), as(leaderId));
        assertThrows(AppException.class, () -> service.callInGame(raid.id(), as(leaderId)));
        service.pending(leaderNick);
        at(now.plusSeconds(10));
        assertThrows(AppException.class, () -> service.callInGame(raid.id(), as(leaderId)));
        at(now.plusSeconds(31));
        assertEquals("WAITING", service.callInGame(raid.id(), as(leaderId)).getFirst().state());
        at(now.plusSeconds(31 + 121));
        assertTrue(service.pending(leaderNick).isEmpty());
        assertEquals("EXPIRED", service.invites(raid.id(), as(leaderId)).getFirst().state());
    }

    @Test void disbandRemovesRaidAndLogs() {
        var raid = service.create("Raid", as(leaderId));
        assertThrows(AppException.class, () -> service.disband(raid.id(), as(outsiderId)));
        service.disband(raid.id(), as(adminId));
        assertTrue(service.list(as(leaderId)).stream().noneMatch(r -> r.id().equals(raid.id())));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE type='RAID_DISBANDED' AND actor_id=?", Integer.class, adminId));
    }

    @Test void levelComesFromTheLatestGameStatus() {
        var raid = service.create("Raid", as(leaderId));
        long other = member();
        service.addMembers(raid.id(), List.of(other), null, false, as(leaderId));
        for (Object[] row : new Object[][]{{1L, 40, "2026-11-01T10:00:00Z"}, {2L, 52, "2026-11-02T10:00:00Z"}})
            jdbc.update("""
                INSERT INTO character_status(member_id,gid,character_name,level,exp_percent,hp,hp_max,fp,fp_max,sp,sp_max,observed_at,updated_at)
                VALUES(?,?,?,?,0,1,1,1,1,1,1,?::timestamptz,?::timestamptz)
                """, leaderId, row[0], leaderNick + row[0], row[1], row[2], row[2]);
        var view = raid(raid.id());
        assertEquals(52, view.members().stream().filter(m -> m.id() == leaderId).findFirst().orElseThrow().level());
        assertEquals(1, view.members().stream().filter(m -> m.id() == other).findFirst().orElseThrow().level());
    }

    @Test void marksMoveLikeInWowAndOnlyManagersSetThem() {
        var raid = service.create("Raid", as(leaderId));
        var ids = many(2);
        service.addMembers(raid.id(), ids, null, false, as(leaderId));
        var view = service.setMark(raid.id(), ids.get(0), 8, as(leaderId));
        assertEquals(8, view.members().stream().filter(m -> m.id().equals(ids.get(0))).findFirst().orElseThrow().mark());
        // a caveira passa para o outro: cada marca em um integrante só
        view = service.setMark(raid.id(), ids.get(1), 8, as(leaderId));
        assertNull(view.members().stream().filter(m -> m.id().equals(ids.get(0))).findFirst().orElseThrow().mark());
        assertEquals(8, view.members().stream().filter(m -> m.id().equals(ids.get(1))).findFirst().orElseThrow().mark());
        assertNull(service.setMark(raid.id(), ids.get(1), null, as(adminId)).members().stream()
            .filter(m -> m.id().equals(ids.get(1))).findFirst().orElseThrow().mark());
        assertThrows(AppException.class, () -> service.setMark(raid.id(), ids.get(0), 1, as(ids.get(1))));
        assertThrows(AppException.class, () -> service.setMark(raid.id(), ids.get(0), 9, as(leaderId)));
        assertThrows(AppException.class, () -> service.setMark(raid.id(), outsiderId, 1, as(leaderId)));
    }

    @Test void hiddenMembersAreNeverInvitedNorCallTheirSubgroup() {
        var raid = service.create("Raid", as(leaderId));
        var ids = many(4);
        service.addMembers(raid.id(), ids, 2, false, as(leaderId));
        service.setGroupLeader(raid.id(), ids.get(0), as(leaderId));
        service.setHidden(raid.id(), ids.get(0), true, as(leaderId));   // o líder do subgrupo 2, oculto
        service.setHidden(raid.id(), ids.get(1), true, as(leaderId));
        assertThrows(AppException.class, () -> service.setHidden(raid.id(), ids.get(2), true, as(ids.get(2))));
        var invites = service.callInGame(raid.id(), as(leaderId));
        assertEquals(1, invites.size());                  // o subgrupo 1 só tem o líder da raid
        var group2 = invites.getFirst();
        assertEquals(2, group2.group());
        assertEquals(nickOf(ids.get(2)), group2.leader());  // chama o primeiro visível
        assertEquals(List.of(nickOf(ids.get(3))), group2.nicks());
        assertTrue(group2.skipped().isEmpty());            // ocultos nem aparecem como "fora do chamado"
    }

    @Test void raidTabCanBeLimitedToARoleAndChosenMembers() {
        long allowed = member(), moderator = member(nick("Mod"), "MODERADOR", "APROVADO");
        var raid = service.create("Raid", as(leaderId));
        // padrão: todo membro aprovado vê
        assertTrue(access.view(as(outsiderId)).canView());
        assertThrows(AppException.class, () -> access.update(Role.MODERADOR, List.of(allowed), as(moderator)));  // só Administrador+

        var view = access.update(Role.ADMINISTRADOR, List.of(allowed, allowed), as(adminId));
        assertEquals(Role.ADMINISTRADOR, view.minRole());
        assertEquals(List.of(allowed), view.viewers().stream().map(RaidAccessService.Viewer::id).toList());
        assertFalse(access.view(as(outsiderId)).canView());
        assertFalse(access.view(as(moderator)).canView());
        assertFalse(access.view(as(leaderId)).canConfigure());
        assertTrue(access.view(as(allowed)).canView());
        assertTrue(access.view(as(adminId)).canView());
        // sem acesso, as rotas da raid recusam, mesmo para quem lidera uma raid
        var denied = assertThrows(AppException.class, () -> service.list(as(outsiderId)));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatus());
        assertThrows(AppException.class, () -> service.addMembers(raid.id(), List.of(allowed), null, false, as(leaderId)));
        assertEquals(1, service.list(as(allowed)).size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE type='RAID_ACCESS_UPDATED' AND actor_id=?", Integer.class, adminId));
    }

    @Test void addedMembersAreHiddenByDefaultAndTheLeaderIsNot() {
        var raid = service.create("Raid", as(leaderId));
        long quiet = member(), shown = member();
        service.addMembers(raid.id(), List.of(quiet), null, as(leaderId));
        var view = service.addMembers(raid.id(), List.of(shown), null, false, as(leaderId));
        assertFalse(view.members().stream().filter(m -> m.id() == leaderId).findFirst().orElseThrow().hidden());
        assertTrue(view.members().stream().filter(m -> m.id() == quiet).findFirst().orElseThrow().hidden());
        assertFalse(view.members().stream().filter(m -> m.id() == shown).findFirst().orElseThrow().hidden());
    }

    @Test void apiRequiresLogin() throws Exception {
        mvc.perform(get("/api/raids")).andExpect(status().is4xxClientError());
    }
}
