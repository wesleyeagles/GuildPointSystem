package com.guild.app.raid.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.*;
import com.guild.app.common.exception.AppException;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.party.service.PartyGameInviteService.InviteNotice;
import com.guild.app.party.service.PartyGameInviteService.PendingInvite;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Raid: grupo sem limite de integrantes, dividido em subgrupos de até 8 (o limite da PT do jogo).
 * Qualquer membro aprovado cria uma raid e vira o líder; o líder ou um organizador
 * (Administrador+) monta os subgrupos. "Chamar no jogo" cria um convite por subgrupo, que a DLL
 * do líder do subgrupo busca pela mesma rota dos convites de PT e executa com "@party Nick".
 */
@Service
@RequiredArgsConstructor
public class RaidService {
    public static final int GROUP_SIZE = 8;
    static final int VALID_SECONDS = 120;
    static final int COOLDOWN_SECONDS = 30;
    private static final String NICK = "[A-Za-z0-9_-]{2,16}";

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final AuditLogService audit;
    private final ObjectMapper json;
    private final WebSocketPublisher ws;
    private final RaidAccessService access;
    Clock clock = Clock.systemUTC();
    /** Sobe a cada mudança confirmada: quem guarda a escalação em cache (RaidLiveService) recarrega. */
    private final AtomicLong version = new AtomicLong();

    /** mark: marca de raid 1..8 (estrela, círculo, losango, triângulo, lua, quadrado, X, caveira); null = nenhuma. */
    public record RaidMemberView(Long id, String nickname, String className, String classImageUrl, int level,
                                 int group, boolean raidLeader, boolean groupLeader, Integer mark, boolean hidden) {}
    public record RaidView(Long id, String name, Long leaderId, String leader, Instant createdAt,
                           boolean canManage, List<RaidMemberView> members) {}
    public record GroupInviteView(int group, UUID id, String leader, String state, List<String> nicks,
                                  List<String> skipped, Instant createdAt, Instant expiresAt) {}

    private record Row(Long raidId, Long memberId, String nickname, String className, String classImageUrl,
                       int level, int group, boolean groupLeader, Integer mark, boolean hidden, Instant joinedAt) {}

    // ---- leitura ----

    @Transactional(readOnly = true)
    public List<RaidView> list(MemberPrincipal actor) {
        Member viewer = approved(actor);
        var rows = rows(null);
        return jdbc.query("""
            SELECT r.id,r.name,r.leader_id,m.nickname,r.created_at FROM raid r JOIN member m ON m.id=r.leader_id ORDER BY r.created_at
            """, (r, n) -> {
            long id = r.getLong("id");
            long leaderId = r.getLong("leader_id");
            return forViewer(view(id, r.getString("name"), leaderId, r.getString("nickname"), r.getTimestamp("created_at").toInstant(),
                canManage(viewer, leaderId), rows.getOrDefault(id, List.of())));
        });
    }

    /** Integrantes ocultos só aparecem para quem gerencia a raid (líder ou Administrador+). */
    static RaidView forViewer(RaidView raid) {
        if (raid.canManage()) return raid;
        return new RaidView(raid.id(), raid.name(), raid.leaderId(), raid.leader(), raid.createdAt(), false,
            raid.members().stream().filter(m -> !m.hidden()).toList());
    }

    /** Todas as raids, sem permissões de quem vê (canManage=false): para o status ao vivo. */
    @Transactional(readOnly = true)
    public List<RaidView> all() {
        var rows = rows(null);
        return jdbc.query("""
            SELECT r.id,r.name,r.leader_id,m.nickname,r.created_at FROM raid r JOIN member m ON m.id=r.leader_id ORDER BY r.created_at
            """, (r, n) -> {
            long id = r.getLong("id");
            return view(id, r.getString("name"), r.getLong("leader_id"), r.getString("nickname"),
                r.getTimestamp("created_at").toInstant(), false, rows.getOrDefault(id, List.of()));
        });
    }

    public long version() { return version.get(); }

    @Transactional(readOnly = true)
    public List<GroupInviteView> invites(Long raidId, MemberPrincipal actor) {
        approved(actor);
        return inviteViews(raidId);
    }

    // ---- montagem ----

    @Transactional
    public RaidView create(String name, MemberPrincipal actor) {
        Member member = approved(actor);
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > 60) throw new AppException("Informe um nome de até 60 caracteres.", HttpStatus.BAD_REQUEST);
        if (raidOf(member.getId()) != null) {
            // Oculto não pode saber que está numa raid: mensagem genérica.
            boolean hidden = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT bool_or(hidden) FROM raid_member WHERE member_id=?", Boolean.class, member.getId()));
            throw new AppException(hidden ? "Não foi possível criar a raid agora." : "Você já está em uma raid.", HttpStatus.CONFLICT);
        }
        Long id = jdbc.queryForObject("INSERT INTO raid(name,leader_id) VALUES(?,?) RETURNING id", Long.class, clean, member.getId());
        jdbc.update("INSERT INTO raid_member(raid_id,member_id,group_no) VALUES(?,?,1)", id, member.getId());
        audit.log(AuditLogType.RAID_CREATED, member, member, Map.of("raidId", id, "name", clean));
        changed();
        return find(id, member);
    }

    @Transactional
    public void disband(Long raidId, MemberPrincipal actor) {
        Member member = approved(actor);
        var raid = manageable(raidId, member);
        jdbc.update("DELETE FROM raid WHERE id=?", raidId);
        audit.log(AuditLogType.RAID_DISBANDED, member, member, Map.of("raidId", raidId, "name", raid.name()));
        changed();
    }

    /**
     * Adiciona membros aprovados. Sem subgrupo, cada um vai para o primeiro com vaga.
     * Quem já está nesta raid é ignorado; quem está em outra recusa a operação inteira.
     */
    @Transactional
    public RaidView addMembers(Long raidId, List<Long> memberIds, Integer group, MemberPrincipal actor) {
        Member member = approved(actor);
        manageable(raidId, member);
        if (memberIds == null || memberIds.isEmpty()) throw new AppException("Escolha ao menos um membro.", HttpStatus.BAD_REQUEST);
        if (group != null && group < 1) throw new AppException("Subgrupo inválido.", HttpStatus.BAD_REQUEST);
        var sizes = groupSizes(raidId);
        var busy = new ArrayList<String>();
        var toAdd = new ArrayList<Member>();
        for (Long id : new LinkedHashSet<>(memberIds)) {
            Member m = members.findById(id).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
            if (!eligible(m)) throw new AppException(m.getNickname() + " não é um membro aprovado.", HttpStatus.BAD_REQUEST);
            Long current = raidOf(m.getId());
            if (raidId.equals(current)) continue;
            if (current != null) busy.add(m.getNickname());
            else toAdd.add(m);
        }
        if (!busy.isEmpty()) throw new AppException("Já estão em outra raid: " + String.join(", ", busy) + ".", HttpStatus.CONFLICT);
        for (Member m : toAdd) {
            int target = group != null ? group : firstFreeGroup(sizes);
            if (sizes.getOrDefault(target, 0) >= GROUP_SIZE)
                throw new AppException("O subgrupo " + target + " já tem " + GROUP_SIZE + " integrantes.", HttpStatus.CONFLICT);
            jdbc.update("INSERT INTO raid_member(raid_id,member_id,group_no) VALUES(?,?,?)", raidId, m.getId(), target);
            sizes.merge(target, 1, Integer::sum);
        }
        changed();
        return find(raidId, member);
    }

    /** O líder ou um organizador tira alguém; qualquer um sai sozinho. Sem integrantes, a raid acaba. */
    @Transactional
    public void removeMember(Long raidId, Long memberId, MemberPrincipal actor) {
        Member member = approved(actor);
        var raid = locked(raidId);
        if (!memberId.equals(member.getId()) && !canManage(member, raid.leaderId()))
            throw new AppException("Apenas o líder da raid ou um organizador pode tirar integrantes.", HttpStatus.FORBIDDEN);
        if (jdbc.update("DELETE FROM raid_member WHERE raid_id=? AND member_id=?", raidId, memberId) == 0)
            throw new AppException("Este membro não está na raid.", HttpStatus.NOT_FOUND);
        if (memberId.equals(raid.leaderId())) {
            var next = jdbc.queryForList("SELECT member_id FROM raid_member WHERE raid_id=? ORDER BY joined_at LIMIT 1", Long.class, raidId);
            if (next.isEmpty()) {
                jdbc.update("DELETE FROM raid WHERE id=?", raidId);
                audit.log(AuditLogType.RAID_DISBANDED, member, member, Map.of("raidId", raidId, "name", raid.name()));
            } else {
                jdbc.update("UPDATE raid SET leader_id=? WHERE id=?", next.getFirst(), raidId);
            }
        }
        changed();
    }

    @Transactional
    public RaidView moveMember(Long raidId, Long memberId, int group, MemberPrincipal actor) {
        Member member = approved(actor);
        manageable(raidId, member);
        if (group < 1) throw new AppException("Subgrupo inválido.", HttpStatus.BAD_REQUEST);
        Integer current = groupOf(raidId, memberId);
        if (current == null) throw new AppException("Este membro não está na raid.", HttpStatus.NOT_FOUND);
        if (current != group) {
            if (groupSizes(raidId).getOrDefault(group, 0) >= GROUP_SIZE)
                throw new AppException("O subgrupo " + group + " já tem " + GROUP_SIZE + " integrantes.", HttpStatus.CONFLICT);
            jdbc.update("UPDATE raid_member SET group_no=?,group_leader=FALSE WHERE raid_id=? AND member_id=?", group, raidId, memberId);
            changed();
        }
        return find(raidId, member);
    }

    @Transactional
    public RaidView transferLeader(Long raidId, Long memberId, MemberPrincipal actor) {
        Member member = approved(actor);
        manageable(raidId, member);
        if (groupOf(raidId, memberId) == null) throw new AppException("Este membro não está na raid.", HttpStatus.NOT_FOUND);
        jdbc.update("UPDATE raid SET leader_id=? WHERE id=?", memberId, raidId);
        changed();
        return find(raidId, member);
    }

    /** Quem chama o subgrupo no jogo (a DLL dele digita os convites). */
    @Transactional
    public RaidView setGroupLeader(Long raidId, Long memberId, MemberPrincipal actor) {
        Member member = approved(actor);
        manageable(raidId, member);
        Integer group = groupOf(raidId, memberId);
        if (group == null) throw new AppException("Este membro não está na raid.", HttpStatus.NOT_FOUND);
        jdbc.update("UPDATE raid_member SET group_leader=FALSE WHERE raid_id=? AND group_no=?", raidId, group);
        jdbc.update("UPDATE raid_member SET group_leader=TRUE WHERE raid_id=? AND member_id=?", raidId, memberId);
        changed();
        return find(raidId, member);
    }

    /**
     * Marca de raid num integrante (1..8) ou nenhuma (null). Como no WoW, cada marca fica num integrante só:
     * marcar outro tira a marca de quem a tinha.
     */
    @Transactional
    public RaidView setMark(Long raidId, Long memberId, Integer mark, MemberPrincipal actor) {
        Member member = approved(actor);
        manageable(raidId, member);
        if (mark != null && (mark < 1 || mark > 8)) throw new AppException("Marca inválida.", HttpStatus.BAD_REQUEST);
        if (groupOf(raidId, memberId) == null) throw new AppException("Este membro não está na raid.", HttpStatus.NOT_FOUND);
        if (mark != null) jdbc.update("UPDATE raid_member SET mark=NULL WHERE raid_id=? AND mark=? AND member_id<>?", raidId, mark, memberId);
        jdbc.update("UPDATE raid_member SET mark=? WHERE raid_id=? AND member_id=?", mark, raidId, memberId);
        changed();
        return find(raidId, member);
    }

    /**
     * Integrante oculto: a DLL dele continua enviando o status, mas ele não vê a raid (sem painel no jogo,
     * fora dos convites e fora da raid no site para quem não gerencia).
     */
    @Transactional
    public RaidView setHidden(Long raidId, Long memberId, boolean hidden, MemberPrincipal actor) {
        Member member = approved(actor);
        manageable(raidId, member);
        if (jdbc.update("UPDATE raid_member SET hidden=? WHERE raid_id=? AND member_id=?", hidden, raidId, memberId) == 0)
            throw new AppException("Este membro não está na raid.", HttpStatus.NOT_FOUND);
        changed();
        return find(raidId, member);
    }

    // ---- convites pelo jogo ----

    /** Um convite por subgrupo com alguém para chamar; substitui os anteriores da raid. */
    @Transactional
    public List<GroupInviteView> callInGame(Long raidId, MemberPrincipal actor) {
        Member member = approved(actor);
        var raid = manageable(raidId, member);
        Instant now = clock.instant();
        for (var previous : inviteViews(raidId)) {
            if (previous.state().equals("WAITING"))
                throw new AppException("Já existe um chamado aguardando o jogo de um líder de subgrupo.", HttpStatus.CONFLICT);
            if (now.isBefore(previous.createdAt().plusSeconds(COOLDOWN_SECONDS)))
                throw new AppException("Aguarde alguns segundos antes de chamar de novo.", HttpStatus.CONFLICT);
        }
        var view = find(raidId, member);
        var byGroup = view.members().stream().collect(Collectors.groupingBy(RaidMemberView::group, TreeMap::new, Collectors.toList()));
        var created = new ArrayList<Map<String, Object>>();
        jdbc.update("DELETE FROM raid_game_invite WHERE raid_id=?", raidId);
        for (var entry : byGroup.entrySet()) {
            // Ocultos ficam de fora: nem são convidados, nem chamam o subgrupo (a DLL deles digitaria os convites).
            var visible = entry.getValue().stream().filter(m -> !m.hidden()).toList();
            if (visible.isEmpty()) continue;
            var lead = visible.stream().filter(RaidMemberView::groupLeader).findFirst().orElse(visible.getFirst());
            Member leader = members.findById(lead.id()).orElseThrow();
            if (!eligible(leader) || !leader.getNickname().matches(NICK)) continue;
            var nicks = new ArrayList<String>();
            var skipped = new ArrayList<String>();
            for (var m : visible) {
                if (m.id().equals(lead.id())) continue;
                Member full = members.findById(m.id()).orElseThrow();
                if (eligible(full) && full.getNickname().matches(NICK)) nicks.add(full.getNickname());
                else skipped.add(full.getNickname());
            }
            if (nicks.isEmpty()) continue;
            UUID id = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO raid_game_invite(raid_id,group_no,id,leader_id,actor_id,nicks,skipped,created_at,expires_at)
                VALUES(?,?,?,?,?,?::jsonb,?::jsonb,?,?)
                """, raidId, entry.getKey(), id, leader.getId(), member.getId(), encode(nicks), encode(skipped),
                Timestamp.from(now), Timestamp.from(now.plusSeconds(VALID_SECONDS)));
            created.add(Map.of("group", entry.getKey(), "leader", leader.getNickname(), "nicks", nicks));
        }
        if (created.isEmpty()) throw new AppException("Nenhum subgrupo tem integrantes para chamar no jogo.", HttpStatus.BAD_REQUEST);
        audit.log(AuditLogType.RAID_GAME_INVITE, member, member, Map.of("raidId", raidId, "name", raid.name(), "groups", created));
        return inviteViews(raidId);
    }

    /** Busca da DLL (pela rota dos convites de PT): entrega uma vez o convite do subgrupo deste líder. */
    @Transactional
    public Optional<PendingInvite> pending(String character) {
        Instant now = clock.instant();
        for (Member leader : members.findByGameCharacter(character)) {
            if (!eligible(leader)) continue;
            var rows = jdbc.queryForList("""
                SELECT raid_id,group_no,id,nicks FROM raid_game_invite
                WHERE leader_id=? AND delivered_at IS NULL AND expires_at>? ORDER BY created_at LIMIT 1 FOR UPDATE
                """, leader.getId(), Timestamp.from(now));
            if (rows.isEmpty()) continue;
            var row = rows.getFirst();
            jdbc.update("UPDATE raid_game_invite SET delivered_at=? WHERE id=?", Timestamp.from(now), row.get("id"));
            return Optional.of(new PendingInvite((UUID) row.get("id"), decode(row.get("nicks").toString())));
        }
        return Optional.empty();
    }

    @Transactional
    public boolean done(UUID id, String character) {
        for (Member leader : members.findByGameCharacter(character)) {
            var rows = jdbc.queryForList("""
                UPDATE raid_game_invite g SET completed_at=? FROM raid r
                WHERE g.id=? AND g.leader_id=? AND g.delivered_at IS NOT NULL AND g.completed_at IS NULL AND r.id=g.raid_id
                RETURNING g.group_no,g.nicks,r.name
                """, Timestamp.from(clock.instant()), id, leader.getId());
            if (rows.isEmpty()) continue;
            var row = rows.getFirst();
            String message = leader.getNickname() + " te convidou para o subgrupo " + row.get("group_no")
                + " da raid " + row.get("name") + " no jogo.";
            for (String nick : decode(row.get("nicks").toString()))
                for (Member invited : members.findByGameCharacter(nick))
                    ws.publishPartyMemberNotice(invited.getId(), new InviteNotice("PARTY_GAME_INVITE", message, null, id));
            return true;
        }
        return false;
    }

    // ---- apoio ----

    private record RaidHead(Long id, String name, Long leaderId) {}

    /** Membro aprovado com acesso à aba Raid (RaidAccessService); todas as rotas do site passam por aqui. */
    private Member approved(MemberPrincipal actor) {
        var member = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!eligible(member)) throw new AppException("Acesso negado", HttpStatus.FORBIDDEN);
        access.requireView(member);
        return member;
    }
    static boolean eligible(Member member) {
        return member.getStatus() == MemberStatus.APROVADO && member.isProfileComplete()
            && member.getDeletedAt() == null && member.getSuspendedAt() == null;
    }
    private static boolean canManage(Member member, Long leaderId) {
        return member.getId().equals(leaderId) || member.getRole().isAtLeast(Role.ADMINISTRADOR);
    }

    /** Trava a raid: operações concorrentes de montagem ficam em fila. */
    private RaidHead locked(Long raidId) {
        return jdbc.query("SELECT id,name,leader_id FROM raid WHERE id=? FOR UPDATE",
                (r, n) -> new RaidHead(r.getLong("id"), r.getString("name"), r.getLong("leader_id")), raidId)
            .stream().findFirst().orElseThrow(() -> new AppException("Raid não encontrada", HttpStatus.NOT_FOUND));
    }
    private RaidHead manageable(Long raidId, Member member) {
        var raid = locked(raidId);
        if (!canManage(member, raid.leaderId()))
            throw new AppException("Apenas o líder da raid ou um organizador pode alterá-la.", HttpStatus.FORBIDDEN);
        return raid;
    }

    private Long raidOf(Long memberId) {
        return jdbc.queryForList("SELECT raid_id FROM raid_member WHERE member_id=?", Long.class, memberId).stream().findFirst().orElse(null);
    }
    private Integer groupOf(Long raidId, Long memberId) {
        return jdbc.queryForList("SELECT group_no FROM raid_member WHERE raid_id=? AND member_id=?", Integer.class, raidId, memberId)
            .stream().findFirst().orElse(null);
    }
    private Map<Integer, Integer> groupSizes(Long raidId) {
        var sizes = new HashMap<Integer, Integer>();
        jdbc.query("SELECT group_no,count(*) c FROM raid_member WHERE raid_id=? GROUP BY group_no",
            r -> { sizes.put(r.getInt("group_no"), r.getInt("c")); }, raidId);
        return sizes;
    }
    static int firstFreeGroup(Map<Integer, Integer> sizes) {
        int group = 1;
        while (sizes.getOrDefault(group, 0) >= GROUP_SIZE) group++;
        return group;
    }

    private RaidView find(Long raidId, Member viewer) {
        var rows = rows(raidId);
        return jdbc.query("SELECT r.id,r.name,r.leader_id,m.nickname,r.created_at FROM raid r JOIN member m ON m.id=r.leader_id WHERE r.id=?",
            (r, n) -> {
                long leaderId = r.getLong("leader_id");
                return view(raidId, r.getString("name"), leaderId, r.getString("nickname"), r.getTimestamp("created_at").toInstant(),
                    canManage(viewer, leaderId), rows.getOrDefault(raidId, List.of()));
            }, raidId).stream().findFirst().orElseThrow(() -> new AppException("Raid não encontrada", HttpStatus.NOT_FOUND));
    }

    /** Level do jogo: o do último status enviado pela DLL (como na Party); sem status, o do cadastro. */
    private Map<Long, List<Row>> rows(Long raidId) {
        String sql = """
            SELECT rm.raid_id,m.id,m.nickname,c.name class_name,c.image_url,COALESCE(gs.level,m.level,1) AS level,
                   rm.group_no,rm.group_leader,rm.mark,rm.hidden,rm.joined_at
            FROM raid_member rm JOIN member m ON m.id=rm.member_id LEFT JOIN character_class c ON c.id=m.class_id
            LEFT JOIN LATERAL (SELECT s.level FROM character_status s WHERE s.member_id=m.id ORDER BY s.updated_at DESC LIMIT 1) gs ON TRUE
            """ + (raidId != null ? "WHERE rm.raid_id=? " : "") + "ORDER BY rm.group_no,rm.joined_at,m.id";
        Object[] args = raidId != null ? new Object[]{raidId} : new Object[0];
        return jdbc.query(sql, (r, n) -> new Row(r.getLong("raid_id"), r.getLong("id"), r.getString("nickname"),
                r.getString("class_name"), r.getString("image_url"), r.getInt("level"), r.getInt("group_no"),
                r.getBoolean("group_leader"), (Integer) r.getObject("mark", Integer.class), r.getBoolean("hidden"),
                r.getTimestamp("joined_at").toInstant()), args)
            .stream().collect(Collectors.groupingBy(Row::raidId, LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * Líder de cada subgrupo: o marcado; sem marca, o líder da raid se estiver nele; senão quem entrou primeiro.
     * Dentro do subgrupo, o líder vem primeiro.
     */
    private static RaidView view(Long id, String name, Long leaderId, String leader, Instant createdAt, boolean canManage, List<Row> rows) {
        var leaders = new HashMap<Integer, Long>();
        for (Row r : rows) if (r.groupLeader()) leaders.put(r.group(), r.memberId());
        for (Row r : rows) if (r.memberId().equals(leaderId)) leaders.putIfAbsent(r.group(), r.memberId());
        for (Row r : rows) leaders.putIfAbsent(r.group(), r.memberId());
        var out = rows.stream()
            .map(r -> new RaidMemberView(r.memberId(), r.nickname(), r.className(), r.classImageUrl(), r.level(), r.group(),
                r.memberId().equals(leaderId), r.memberId().equals(leaders.get(r.group())), r.mark(), r.hidden()))
            .sorted(Comparator.comparingInt(RaidMemberView::group).thenComparing(m -> !m.groupLeader()))
            .toList();
        return new RaidView(id, name, leaderId, leader, createdAt, canManage, out);
    }

    private List<GroupInviteView> inviteViews(Long raidId) {
        Instant now = clock.instant();
        return jdbc.query("""
            SELECT g.*,m.nickname FROM raid_game_invite g JOIN member m ON m.id=g.leader_id WHERE g.raid_id=? ORDER BY g.group_no
            """, (r, n) -> {
            Instant expires = r.getTimestamp("expires_at").toInstant();
            String state = r.getTimestamp("completed_at") != null ? "COMPLETE"
                : r.getTimestamp("delivered_at") != null ? "DELIVERED"
                : now.isBefore(expires) ? "WAITING" : "EXPIRED";
            return new GroupInviteView(r.getInt("group_no"), (UUID) r.getObject("id"), r.getString("nickname"), state,
                decode(r.getString("nicks")), decode(r.getString("skipped")), r.getTimestamp("created_at").toInstant(), expires);
        }, raidId);
    }

    private void changed() {
        ws.publishRaids();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { version.incrementAndGet(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { version.incrementAndGet(); }
        });
    }
    private String encode(List<String> value) {
        try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private List<String> decode(String value) {
        try { return json.readValue(value, new TypeReference<List<String>>() {}); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
