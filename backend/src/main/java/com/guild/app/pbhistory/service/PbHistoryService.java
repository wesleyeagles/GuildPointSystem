package com.guild.app.pbhistory.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Histórico de PB. A DLL de todo membro aprovado recebe a lista de bosses ({@code pb_boss}) e, ao ver um deles
 * na lista de monstros do jogo, avisa o site: vivo a cada ~10 s (presença) e a morte na hora em que o jogo a marca.
 * Cada boss que nasce (serial do jogo) é um registro; presente = viu o boss vivo E estava perto na morte.
 * Nos minutos depois de cada horário de PB a DLL manda também os nomes de monstros que vê: aparecem como
 * candidatos e a liderança escolhe quais são bosses. Só histórico (não dá pontos). Página só de Administrador+.
 * Como os outros envios da DLL, vai pelo nome do personagem (sem login).
 */
@Service
@RequiredArgsConstructor
public class PbHistoryService {
    public static final int MAX_LINES = 200, MAX_NAME = 64, MAX_COORDINATE = 1_000_000;
    /** O mesmo serial do mesmo boss no mesmo mapa dentro deste tempo é o mesmo boss. */
    public static final Duration SAME_BOSS = Duration.ofHours(6);
    /** Visto vivo até este tempo antes da morte conta como "perto na morte" (o aviso da morte pode se perder). */
    public static final Duration NEAR_DEATH = Duration.ofSeconds(15);
    /** Avisos de morte de outros membros que chegam até este tempo depois ainda marcam presença. */
    public static final Duration LATE_DEATH = Duration.ofMinutes(2);
    public static final String ROTACAO = "ROTACAO", EXTRA = "EXTRA";

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final AuditLogService audit;
    private Clock clock = Clock.systemUTC();

    /** Testes: relógio fixo (pelo proxy do Spring chega no serviço de verdade). */
    public void useClock(Clock value) { clock = value; }

    public record Boss(String name, String label, String kind, long kills, Instant lastKillAt) {}
    public record Candidate(String name, String mapName, int seenCount, Instant lastSeenAt) {}
    public record Overview(boolean canView, List<Boss> bosses, List<Candidate> candidates) {}
    public record Presence(long memberId, String nickname, Instant firstSeenAt, Instant lastSeenAt, boolean present) {}
    /**
     * diedAt null = ninguém viu a morte (sumiu longe, ou ainda vivo). minHp = menor HP visto (null = não leu).
     * x/z = última posição em que foi visto, no mundo do jogo (as mesmas unidades da posição da raid).
     */
    public record Kill(long id, String boss, String label, String kind, int mapCode, String mapName, int x, int z, Instant firstSeenAt,
                       Instant diedAt, Integer minHp, List<Presence> members) {}
    /** Limites do mapa no mundo, como a janela do mapa do jogo usa (a DLL envia na linha "G"). */
    public record MapBounds(int map, int minX, int minZ, int maxX, int maxZ) {}
    public record AttendanceRow(long memberId, String nickname, long present, long seen) {}
    public record Attendance(int days, long kills, List<AttendanceRow> rows) {}

    private static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.isProfileComplete()
            && m.getDeletedAt() == null && m.getSuspendedAt() == null;
    }
    private static boolean admin(Member m) { return eligible(m) && m.getRole().isAtLeast(Role.ADMINISTRADOR); }

    /** Membro aprovado que usa este personagem. */
    @Transactional(readOnly = true)
    public Optional<Member> user(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        return members.findByGameCharacter(character.strip()).stream().filter(PbHistoryService::eligible).findFirst();
    }

    static boolean validName(String name) {
        if (name.isEmpty() || name.length() > MAX_NAME || name.startsWith("[")) return false;
        for (char c : name.toCharArray()) if (c < 0x20 || c > 0x7E) return false;
        return true;
    }

    /**
     * Envio da DLL no mapa {@code map} (índice do jogo). Corpo (UTF-8, uma linha por item, campos separados por TAB):
     * <ul>
     *   <li>{@code M nomeDoMapa}: nome visível do mapa (título do mapa grande);</li>
     *   <li>{@code C nome}: monstro visto logo depois de um horário de PB (candidato a boss);</li>
     *   <li>{@code P serial x z hp nome}: boss da lista vivo por perto (hp 0..100, -1 = não leu);</li>
     *   <li>{@code D serial nome}: o jogo marcou a morte do boss com este personagem perto.</li>
     * </ul>
     * Nomes sem o "[nível]" do começo. Resposta: {@code A 1} e uma linha {@code B nome} por boss acompanhado.
     * 403 personagem que não é de membro aprovado; 400 pedido grande demais.
     */
    @Transactional
    public String game(String character, int map, String body) {
        Member member = user(character).orElseThrow(() ->
            new AppException("Personagem não é de membro aprovado.", HttpStatus.FORBIDDEN));
        if (map < 0 || map > 1000) throw new AppException("Mapa inválido.", HttpStatus.BAD_REQUEST);
        List<String> lines = body == null || body.isBlank() ? List.of() : List.of(body.replace("\r", "").split("\n"));
        if (lines.size() > MAX_LINES) throw new AppException("Linhas demais.", HttpStatus.BAD_REQUEST);
        Instant now = clock.instant();
        String mapName = null;
        for (String line : lines) {
            String[] f = line.split("\t", -1);
            if (f.length == 2 && f[0].equals("M") && validName(f[1].strip())) mapName = f[1].strip();
        }
        Set<String> bosses = new HashSet<>(jdbc.queryForList("SELECT name FROM pb_boss", String.class));
        for (String line : lines) {
            String[] f = line.split("\t", -1);
            if (f.length == 2 && f[0].equals("C")) candidate(f[1].strip(), mapName, bosses, now);
            else if (f.length == 6 && f[0].equals("P")) alive(member, map, mapName, f, bosses, now);
            else if (f.length == 3 && f[0].equals("D")) death(member, map, f, bosses, now);
            else if (f.length == 5 && f[0].equals("G")) bounds(map, f, now);
        }
        StringBuilder sb = new StringBuilder("A\t1\n");
        jdbc.queryForList("SELECT name FROM pb_boss ORDER BY name", String.class)
            .forEach(n -> sb.append("B\t").append(n).append('\n'));
        return sb.toString();
    }

    private void candidate(String name, String mapName, Set<String> bosses, Instant now) {
        if (!validName(name) || bosses.contains(name)) return;
        jdbc.update("""
            INSERT INTO pb_candidate(name,map_name,first_seen_at,last_seen_at) VALUES(?,?,?,?)
            ON CONFLICT (name) DO UPDATE SET seen_count=pb_candidate.seen_count+1, last_seen_at=EXCLUDED.last_seen_at,
                map_name=coalesce(EXCLUDED.map_name, pb_candidate.map_name)""",
            name, mapName, Timestamp.from(now), Timestamp.from(now));
    }

    /** Trava pelo boss (serial + nome + mapa): dois membros vendo o mesmo boss ao mesmo tempo viram um registro só. */
    private void lock(int map, long serial, String name) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))::text", String.class, "pb:" + map + ":" + serial + ":" + name);
    }

    private record Open(long id, Instant diedAt) {}

    private Optional<Open> find(int map, long serial, String name, Instant now) {
        return jdbc.query("""
            SELECT id,died_at FROM pb_kill WHERE map_code=? AND serial=? AND boss_name=? AND first_seen_at>?
            ORDER BY id DESC LIMIT 1""",
            (r, n) -> { Timestamp d = r.getTimestamp("died_at"); return new Open(r.getLong("id"), d == null ? null : d.toInstant()); },
            map, serial, name, Timestamp.from(now.minus(SAME_BOSS))).stream().findFirst();
    }

    private void alive(Member member, int map, String mapName, String[] f, Set<String> bosses, Instant now) {
        long serial;
        int x, z, hp;
        try {
            serial = Long.parseLong(f[1]);
            x = Integer.parseInt(f[2]);
            z = Integer.parseInt(f[3]);
            hp = Integer.parseInt(f[4]);
        } catch (NumberFormatException e) { return; }
        String name = f[5].strip();
        if (serial < 0 || Math.abs(x) > MAX_COORDINATE || Math.abs(z) > MAX_COORDINATE || hp < -1 || hp > 100) return;
        if (!bosses.contains(name)) return;
        lock(map, serial, name);
        Integer minHp = hp < 0 ? null : hp;
        Optional<Open> open = find(map, serial, name, now);
        long id;
        if (open.isEmpty()) {
            id = jdbc.queryForObject("""
                INSERT INTO pb_kill(boss_name,serial,map_code,map_name,x,z,first_seen_at,last_seen_at,min_hp,found_by)
                VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING id""", Long.class,
                name, serial, map, mapName, x, z, Timestamp.from(now), Timestamp.from(now), minHp, member.getId());
        } else {
            if (open.get().diedAt() != null) return;   // já morreu: aviso atrasado de "vivo" não conta
            id = open.get().id();
            jdbc.update("""
                UPDATE pb_kill SET last_seen_at=?, x=?, z=?, map_name=coalesce(?, map_name),
                    min_hp=CASE WHEN ?::smallint IS NULL THEN min_hp ELSE least(coalesce(min_hp,100), ?::smallint) END
                WHERE id=?""", Timestamp.from(now), x, z, mapName, minHp, minHp, id);
        }
        jdbc.update("""
            INSERT INTO pb_presence(kill_id,member_id,first_seen_at,last_seen_at) VALUES(?,?,?,?)
            ON CONFLICT (kill_id,member_id) DO UPDATE SET last_seen_at=EXCLUDED.last_seen_at""",
            id, member.getId(), Timestamp.from(now), Timestamp.from(now));
    }

    private void death(Member member, int map, String[] f, Set<String> bosses, Instant now) {
        long serial;
        try { serial = Long.parseLong(f[1]); } catch (NumberFormatException e) { return; }
        String name = f[2].strip();
        if (serial < 0 || !bosses.contains(name)) return;
        lock(map, serial, name);
        Optional<Open> open = find(map, serial, name, now);
        if (open.isEmpty()) return;   // ninguém viu vivo: não há o que registrar
        long id = open.get().id();
        if (open.get().diedAt() == null) jdbc.update("UPDATE pb_kill SET died_at=? WHERE id=?", Timestamp.from(now), id);
        else if (open.get().diedAt().isBefore(now.minus(LATE_DEATH))) return;
        // Só quem viu vivo: corpo visto sem ter visto o boss vivo não é presença.
        jdbc.update("UPDATE pb_presence SET saw_death=true WHERE kill_id=? AND member_id=?", id, member.getId());
    }

    // ---------------------------------------------------------------- site

    private Member adminOrThrow(MemberPrincipal actor) {
        Member member = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!admin(member)) throw new AppException("Apenas Administradores e o Líder veem o histórico de PB.", HttpStatus.FORBIDDEN);
        return member;
    }

    @Transactional(readOnly = true)
    public Overview overview(MemberPrincipal actor) {
        Member member = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!admin(member)) return new Overview(false, List.of(), List.of());
        List<Boss> bosses = jdbc.query("""
            SELECT b.name,b.label,b.kind,
                (SELECT count(*) FROM pb_kill k WHERE k.boss_name=b.name AND k.died_at IS NOT NULL) AS kills,
                (SELECT max(k.died_at) FROM pb_kill k WHERE k.boss_name=b.name) AS last_kill
            FROM pb_boss b ORDER BY b.kind DESC, lower(coalesce(b.label,b.name))""",
            (r, n) -> { Timestamp t = r.getTimestamp("last_kill");
                return new Boss(r.getString("name"), r.getString("label"), r.getString("kind"), r.getLong("kills"), t == null ? null : t.toInstant()); });
        List<Candidate> candidates = jdbc.query("""
            SELECT c.name,c.map_name,c.seen_count,c.last_seen_at FROM pb_candidate c
            WHERE NOT c.ignored AND NOT EXISTS (SELECT 1 FROM pb_boss b WHERE b.name=c.name)
            ORDER BY c.seen_count DESC, c.name LIMIT 300""",
            (r, n) -> new Candidate(r.getString("name"), r.getString("map_name"), r.getInt("seen_count"), r.getTimestamp("last_seen_at").toInstant()));
        return new Overview(true, bosses, candidates);
    }

    /** Presente = estava perto na morte (avisou a morte ou foi visto vivo até 15 s antes dela). */
    private static final String PRESENT = "(p.saw_death OR (k.died_at IS NOT NULL AND p.last_seen_at >= k.died_at - interval '15 seconds'))";

    /** "G minX minZ maxX maxZ": limites do mapa atual lidos da janela do mapa do jogo. Inválido = ignorado. */
    private void bounds(int map, String[] f, Instant now) {
        int minX, minZ, maxX, maxZ;
        try {
            minX = Integer.parseInt(f[1].strip()); minZ = Integer.parseInt(f[2].strip());
            maxX = Integer.parseInt(f[3].strip()); maxZ = Integer.parseInt(f[4].strip());
        } catch (NumberFormatException e) { return; }
        // A janela guarda os limites em 16 bits (caixa do .bsp).
        for (int v : new int[] {minX, minZ, maxX, maxZ}) if (v < Short.MIN_VALUE || v > Short.MAX_VALUE) return;
        if (minX >= maxX || minZ >= maxZ) return;
        jdbc.update("""
            INSERT INTO game_map_bounds(map_code,min_x,min_z,max_x,max_z,updated_at) VALUES(?,?,?,?,?,?)
            ON CONFLICT (map_code) DO UPDATE SET min_x=EXCLUDED.min_x, min_z=EXCLUDED.min_z, max_x=EXCLUDED.max_x,
                max_z=EXCLUDED.max_z, updated_at=EXCLUDED.updated_at""", map, minX, minZ, maxX, maxZ, Timestamp.from(now));
    }

    /** Limites dos mapas que alguma DLL já enviou (para a bolinha do boss no mapa). */
    @Transactional(readOnly = true)
    public List<MapBounds> mapBounds(MemberPrincipal actor) {
        adminOrThrow(actor);
        return jdbc.query("SELECT map_code,min_x,min_z,max_x,max_z FROM game_map_bounds ORDER BY map_code",
            (r, n) -> new MapBounds(r.getInt("map_code"), r.getInt("min_x"), r.getInt("min_z"), r.getInt("max_x"), r.getInt("max_z")));
    }

    /** Do mais novo ao mais antigo; before = id do último já mostrado (paginação). */
    @Transactional(readOnly = true)
    public List<Kill> kills(String boss, Long memberId, Long before, int limit, MemberPrincipal actor) {
        adminOrThrow(actor);
        int size = Math.max(1, Math.min(limit, 100));
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE true");
        if (boss != null && !boss.isBlank()) { where.append(" AND k.boss_name=?"); args.add(boss.strip()); }
        if (memberId != null) { where.append(" AND EXISTS (SELECT 1 FROM pb_presence x WHERE x.kill_id=k.id AND x.member_id=?)"); args.add(memberId); }
        if (before != null) { where.append(" AND k.id<?"); args.add(before); }
        args.add(size);
        record Row(long id, String boss, String label, String kind, int mapCode, String mapName, int x, int z, Instant first, Instant died, Integer minHp) {}
        List<Row> rows = jdbc.query("""
            SELECT k.id,k.boss_name,b.label,coalesce(b.kind,'EXTRA') AS kind,k.map_code,k.map_name,k.x,k.z,k.first_seen_at,k.died_at,k.min_hp
            FROM pb_kill k LEFT JOIN pb_boss b ON b.name=k.boss_name
            """ + where + " ORDER BY k.id DESC LIMIT ?",
            (r, n) -> { Timestamp d = r.getTimestamp("died_at");
                return new Row(r.getLong("id"), r.getString("boss_name"), r.getString("label"), r.getString("kind"), r.getInt("map_code"), r.getString("map_name"),
                    r.getInt("x"), r.getInt("z"), r.getTimestamp("first_seen_at").toInstant(), d == null ? null : d.toInstant(), (Integer) r.getObject("min_hp")); },
            args.toArray());
        if (rows.isEmpty()) return List.of();
        Map<Long, List<Presence>> byKill = new HashMap<>();
        String ids = String.join(",", rows.stream().map(r -> String.valueOf(r.id())).toList());
        jdbc.query("SELECT p.kill_id,p.member_id,m.nickname,p.first_seen_at,p.last_seen_at," + PRESENT + " AS present "
                + "FROM pb_presence p JOIN pb_kill k ON k.id=p.kill_id JOIN member m ON m.id=p.member_id "
                + "WHERE p.kill_id IN (" + ids + ") ORDER BY lower(m.nickname)",
            r -> { byKill.computeIfAbsent(r.getLong("kill_id"), k -> new ArrayList<>()).add(new Presence(r.getLong("member_id"),
                r.getString("nickname"), r.getTimestamp("first_seen_at").toInstant(), r.getTimestamp("last_seen_at").toInstant(),
                r.getBoolean("present"))); });
        return rows.stream().map(r -> new Kill(r.id(), r.boss(), r.label(), r.kind(), r.mapCode(), r.mapName(), r.x(), r.z(), r.first(), r.died(), r.minHp(),
            byKill.getOrDefault(r.id(), List.of()))).toList();
    }

    /** Frequência nos PBs mortos dos últimos {@code days} dias: presente na morte e só visto. */
    @Transactional(readOnly = true)
    public Attendance attendance(int days, MemberPrincipal actor) {
        adminOrThrow(actor);
        int span = Math.max(1, Math.min(days, 365));
        Timestamp since = Timestamp.from(clock.instant().minus(Duration.ofDays(span)));
        Long kills = jdbc.queryForObject("SELECT count(*) FROM pb_kill WHERE died_at IS NOT NULL AND died_at>?", Long.class, since);
        List<AttendanceRow> rows = jdbc.query(
            "SELECT m.id,m.nickname,count(*) FILTER (WHERE " + PRESENT + ") AS present, count(*) AS seen "
                + "FROM pb_presence p JOIN pb_kill k ON k.id=p.kill_id JOIN member m ON m.id=p.member_id "
                + "WHERE k.died_at IS NOT NULL AND k.died_at>? "
                + "GROUP BY m.id,m.nickname ORDER BY present DESC, seen DESC, lower(m.nickname)",
            (r, n) -> new AttendanceRow(r.getLong("id"), r.getString("nickname"), r.getLong("present"), r.getLong("seen")), since);
        return new Attendance(span, kills == null ? 0 : kills, rows);
    }

    /** Administrador+: acompanha um boss (ou troca o apelido/tipo). */
    @Transactional
    public Overview saveBoss(String name, String label, String kind, MemberPrincipal actor) {
        Member member = adminOrThrow(actor);
        String n = name == null ? "" : name.strip();
        if (n.startsWith("[") && n.indexOf(']') > 0) n = n.substring(n.indexOf(']') + 1).strip();   // colou com o nível
        if (!validName(n)) throw new AppException("Nome inválido: use o nome como o jogo escreve, sem o nível.", HttpStatus.BAD_REQUEST);
        if (!ROTACAO.equals(kind) && !EXTRA.equals(kind)) throw new AppException("Tipo inválido.", HttpStatus.BAD_REQUEST);
        String l = label == null || label.isBlank() ? null : label.strip();
        if (l != null && l.length() > MAX_NAME) throw new AppException("Apelido grande demais.", HttpStatus.BAD_REQUEST);
        jdbc.update("""
            INSERT INTO pb_boss(name,label,kind,added_by) VALUES(?,?,?,?)
            ON CONFLICT (name) DO UPDATE SET label=EXCLUDED.label, kind=EXCLUDED.kind""", n, l, kind, member.getId());
        audit.log(AuditLogType.PB_BOSS_UPDATED, member, member, Map.of("name", n, "kind", kind, "label", l == null ? "" : l));
        return overview(actor);
    }

    /** Administrador+: para de acompanhar (o histórico já gravado fica). */
    @Transactional
    public Overview removeBoss(String name, MemberPrincipal actor) {
        Member member = adminOrThrow(actor);
        if (jdbc.update("DELETE FROM pb_boss WHERE name=?", name) == 0) throw new AppException("Boss não encontrado", HttpStatus.NOT_FOUND);
        audit.log(AuditLogType.PB_BOSS_UPDATED, member, member, Map.of("name", name, "removed", true));
        return overview(actor);
    }

    /** Administrador+: esconde um candidato (monstro comum). */
    @Transactional
    public Overview ignoreCandidate(String name, MemberPrincipal actor) {
        adminOrThrow(actor);
        if (jdbc.update("UPDATE pb_candidate SET ignored=true WHERE name=?", name) == 0)
            throw new AppException("Candidato não encontrado", HttpStatus.NOT_FOUND);
        return overview(actor);
    }
}
