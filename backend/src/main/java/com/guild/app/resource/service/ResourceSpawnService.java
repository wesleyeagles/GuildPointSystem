package com.guild.app.resource.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mapa de recursos (flores, minério e cactos). A DLL de todo membro aprovado olha os monstros por perto e,
 * nos mapas de recurso, manda todo mob sem "[nível]" no nome (os recursos não têm nível) com a posição
 * DELE no mundo. Cada mob (serial do jogo) vira um ponto, mesmo que vários membros o vejam; os pontos ficam
 * para sempre. Nome novo entra sozinho como desconhecido e a liderança escolhe o grau.
 * Todos enviam; só quem está na lista (resource_tracker) vê os pontos no jogo (liberação aos poucos).
 * A página do site é só de Administrador+.
 * Como os outros envios da DLL, vai pelo nome do personagem (sem login).
 */
@Service
@RequiredArgsConstructor
public class ResourceSpawnService {
    /** Mapas de recurso, pelo índice do jogo (tabela do mini-mapa) -> nome do título do mapa grande. */
    public static final Map<Integer, String> MAPS = new LinkedHashMap<>();
    static {
        MAPS.put(11, "Sette Desert");
        MAPS.put(2, "Crag Mind Field");
        MAPS.put(10, "Ether of Heaven");
    }
    public static final int MAX_LINES = 200, MAX_NAME = 64, MAX_COORDINATE = 1_000_000;
    /** No jogo: no máximo esta quantidade de pontos de grau 1 (os mais novos); graus 2, 3 e desconhecidos vão todos. */
    public static final int GAME_TIER1 = 2000, GAME_OTHERS = 5000;
    /** O mesmo serial no mesmo mapa dentro deste tempo é o mesmo mob (visto de novo ou por outro membro). */
    public static final Duration SAME_MOB = Duration.ofHours(12);
    /** Mortes que vão ao jogo em todo envio (linha K): cobre o minuto em que o ponto pisca. */
    public static final Duration RECENT_DEATHS = Duration.ofSeconds(90);
    /** Resposta para quem não está na lista: envia, mas não vê pontos. */
    static final String NO_DOTS = "A\t0\n";

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final AuditLogService audit;
    private final WebSocketPublisher websocket;
    private final Map<String, Instant> recent = new ConcurrentHashMap<>();
    Clock clock = Clock.systemUTC();

    public record View(int areaW, int areaH, int offX, int offY, int minX, int maxZ, double scaleX, double scaleY) {}
    public record MapView(int code, String name, View view) {}
    /** tier: 1..3 = grau, 4 = Tower Rift Metal, 0 = não é recurso (ignorado), null = desconhecido (a liderança ainda não escolheu). */
    public record MobView(String name, int map, Integer tier, long spawns, Instant firstSeenAt) {}
    public record Tracker(Long id, String nickname) {}
    /** canView = canConfigure: a página do site é só de Administrador+. */
    public record Overview(boolean canView, boolean canConfigure, List<MapView> maps, List<MobView> mobs, List<Tracker> trackers) {}
    /** diedAt: quando morreu (null = vivo ou não se sabe). */
    public record SpawnView(long id, String name, Integer tier, int x, int z, Instant foundAt, String foundBy, Instant diedAt) {}

    private static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.isProfileComplete()
            && m.getDeletedAt() == null && m.getSuspendedAt() == null;
    }
    private static boolean configurer(Member m) { return eligible(m) && m.getRole().isAtLeast(Role.ADMINISTRADOR); }
    private boolean listed(Long memberId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM resource_tracker WHERE member_id=?)", Boolean.class, memberId));
    }
    /** O site (página de recursos) é só de Administrador+; a lista vale para os pontos no jogo. */
    private boolean viewer(Member m) { return configurer(m); }

    /** Membro aprovado que usa este personagem (todos enviam). */
    @Transactional(readOnly = true)
    public Optional<Member> user(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        return members.findByGameCharacter(character.strip()).stream().filter(ResourceSpawnService::eligible).findFirst();
    }

    /**
     * Envio da DLL no mapa {@code map}. Corpo (UTF-8, uma linha por item, campos separados por TAB):
     * <ul>
     *   <li>{@code S serial x z nome}: um mob sem "[nível]" no nome, com a posição dele no mundo;</li>
     *   <li>{@code V largura altura offX offY minX maxZ escalaX escalaY}: a escala da janela do mapa do jogo.</li>
     * </ul>
     * Resposta: {@code A 0} para quem não está na lista (só envia, não vê nada). Para quem está: {@code A 1},
     * {@code N ultimoId} e uma linha {@code R id x z grau epochSegundos nome} por ponto do mapa com id maior que
     * {@code since} (grau 0 = desconhecido). Com since=0 vai a lista inteira para o jogo.
     * 403 personagem que não é de membro aprovado; 400 mapa que não é de recurso ou pedido grande demais.
     */
    @Transactional
    public String game(String character, int map, long since, String body) {
        Member member = user(character).orElseThrow(() ->
            new AppException("Personagem não é de membro aprovado.", HttpStatus.FORBIDDEN));
        if (!MAPS.containsKey(map)) throw new AppException("Mapa sem recursos.", HttpStatus.BAD_REQUEST);
        List<String> lines = body == null || body.isBlank() ? List.of() : List.of(body.replace("\r", "").split("\n"));
        if (lines.size() > MAX_LINES) throw new AppException("Linhas demais.", HttpStatus.BAD_REQUEST);
        Instant now = clock.instant();
        boolean changed = false;
        for (String line : lines) {
            String[] f = line.split("\t", -1);
            if (f.length == 5 && f[0].equals("S")) changed |= sighting(member, map, f, now);
            else if (f.length == 3 && f[0].equals("D")) changed |= death(map, f, now);
            else if (f.length == 9 && f[0].equals("V")) saveView(map, f, now);
        }
        if (changed) websocket.publishResourcesChanged(map);
        if (!listed(member.getId())) return NO_DOTS;
        return "A\t1\n" + dots(map, Math.max(0, since)) + deaths(map, now);
    }

    /** O mob sumiu da lista com o personagem perto: morreu. Só a primeira notícia vale. */
    private boolean death(int map, String[] f, Instant now) {
        long serial;
        try { serial = Long.parseLong(f[1]); } catch (NumberFormatException e) { return false; }
        String name = f[2].strip();
        if (serial < 0 || !validName(name)) return false;
        return jdbc.update("UPDATE resource_spawn SET died_at=? WHERE map_code=? AND serial=? AND mob_name=? AND died_at IS NULL AND found_at>?",
            Timestamp.from(now), map, serial, name, Timestamp.from(now.minus(SAME_MOB))) > 0;
    }

    /** {@code K id epochSegundos} por ponto do mapa que morreu há pouco (o jogo para de piscar / fica vermelho). */
    private String deaths(int map, Instant now) {
        StringBuilder sb = new StringBuilder();
        jdbc.query("SELECT id,died_at FROM resource_spawn WHERE map_code=? AND died_at>? ORDER BY id",
            r -> { sb.append("K\t").append(r.getLong("id")).append('\t')
                     .append(r.getTimestamp("died_at").toInstant().getEpochSecond()).append('\n'); },
            map, Timestamp.from(now.minus(RECENT_DEATHS)));
        return sb.toString();
    }

    private boolean sighting(Member member, int map, String[] f, Instant now) {
        long serial;
        int x, z;
        try {
            serial = Long.parseLong(f[1]);
            x = Integer.parseInt(f[2]);
            z = Integer.parseInt(f[3]);
        } catch (NumberFormatException e) { return false; }
        String name = f[4].strip();
        if (serial < 0 || Math.abs((long) x) > MAX_COORDINATE || Math.abs((long) z) > MAX_COORDINATE || !validName(name)) return false;
        jdbc.update("INSERT INTO resource_mob(name,map_code,first_seen_at) VALUES(?,?,?) ON CONFLICT (name) DO NOTHING",
            name, map, Timestamp.from(now));
        Integer tier = jdbc.queryForObject("SELECT tier FROM resource_mob WHERE name=?", Integer.class, name);
        if (tier != null && tier == 0) return false;
        // Primeiro na memória (dois membros vendo o mesmo mob ao mesmo tempo), depois no banco (depois de um reinício).
        String key = map + ":" + serial + ":" + name;
        Instant cut = now.minus(SAME_MOB);
        boolean[] fresh = {false};
        recent.compute(key, (k, seen) -> {
            if (seen != null && seen.isAfter(cut)) return seen;
            fresh[0] = true;
            return now;
        });
        if (!fresh[0]) return false;
        if (recent.size() > 50_000) recent.entrySet().removeIf(e -> e.getValue().isBefore(cut));
        boolean known = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM resource_spawn WHERE map_code=? AND serial=? AND mob_name=? AND found_at>?)",
            Boolean.class, map, serial, name, Timestamp.from(cut)));
        if (known) return false;
        jdbc.update("INSERT INTO resource_spawn(map_code,mob_name,serial,x,z,found_at,found_by) VALUES(?,?,?,?,?,?,?)",
            map, name, serial, x, z, Timestamp.from(now), member.getId());
        return true;
    }

    static boolean validName(String name) {
        if (name.isEmpty() || name.length() > MAX_NAME || name.startsWith("[")) return false;
        for (char c : name.toCharArray()) if (c < 0x20 || c > 0x7E) return false;
        return true;
    }

    private void saveView(int map, String[] f, Instant now) {
        try {
            int w = Integer.parseInt(f[1]), h = Integer.parseInt(f[2]), offX = Integer.parseInt(f[3]), offY = Integer.parseInt(f[4]);
            int minX = Integer.parseInt(f[5]), maxZ = Integer.parseInt(f[6]);
            double sx = Double.parseDouble(f[7]), sy = Double.parseDouble(f[8]);
            if (w < 50 || h < 50 || w > 4096 || h > 4096 || Math.abs(offX) > 4096 || Math.abs(offY) > 4096) return;
            if (Math.abs(minX) > MAX_COORDINATE || Math.abs(maxZ) > MAX_COORDINATE) return;
            if (!(sx > 0 && sx < 10 && sy > 0 && sy < 10)) return;
            jdbc.update("""
                INSERT INTO resource_map_view(map_code,area_w,area_h,off_x,off_y,min_x,max_z,scale_x,scale_y,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (map_code) DO UPDATE SET area_w=EXCLUDED.area_w, area_h=EXCLUDED.area_h, off_x=EXCLUDED.off_x,
                    off_y=EXCLUDED.off_y, min_x=EXCLUDED.min_x, max_z=EXCLUDED.max_z, scale_x=EXCLUDED.scale_x,
                    scale_y=EXCLUDED.scale_y, updated_at=EXCLUDED.updated_at""",
                map, w, h, offX, offY, minX, maxZ, sx, sy, Timestamp.from(now));
        } catch (NumberFormatException ignored) { }
    }

    private record Dot(long id, int x, int z, int tier, long epoch, String name) {}

    private String dots(int map, long since) {
        Long max = jdbc.queryForObject("SELECT coalesce(max(id),0) FROM resource_spawn WHERE map_code=?", Long.class, map);
        long last = max == null ? 0 : max;
        String select = "SELECT s.id,s.x,s.z,coalesce(m.tier,0) AS tier,s.found_at,s.mob_name FROM resource_spawn s "
            + "JOIN resource_mob m ON m.name=s.mob_name WHERE s.map_code=? AND s.id>? AND s.id<=? AND coalesce(m.tier,-1)<>0 ";
        org.springframework.jdbc.core.RowMapper<Dot> row = (r, n) -> new Dot(r.getLong("id"), r.getInt("x"), r.getInt("z"),
            r.getInt("tier"), r.getTimestamp("found_at").toInstant().getEpochSecond(), r.getString("mob_name"));
        List<Dot> out = new ArrayList<>();
        if (since == 0) {
            out.addAll(jdbc.query(select + "AND coalesce(m.tier,0)<>1 ORDER BY s.id DESC LIMIT " + GAME_OTHERS, row, map, 0L, last));
            out.addAll(jdbc.query(select + "AND m.tier=1 ORDER BY s.id DESC LIMIT " + GAME_TIER1, row, map, 0L, last));
            out.sort(Comparator.comparingLong(Dot::id));
        } else {
            out.addAll(jdbc.query(select + "ORDER BY s.id LIMIT " + GAME_TIER1, row, map, since, last));
            if (out.size() == GAME_TIER1) last = out.get(out.size() - 1).id();  // o resto vem no próximo envio
        }
        StringBuilder sb = new StringBuilder("N\t").append(Math.max(last, since)).append('\n');
        for (Dot d : out)
            sb.append("R\t").append(d.id()).append('\t').append(d.x()).append('\t').append(d.z()).append('\t')
              .append(d.tier()).append('\t').append(d.epoch()).append('\t').append(d.name()).append('\n');
        return sb.toString();
    }

    // ---------------------------------------------------------------- site

    private Member member(MemberPrincipal actor) {
        return members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Overview overview(MemberPrincipal actor) {
        Member member = member(actor);
        if (!viewer(member)) return new Overview(false, false, List.of(), List.of(), List.of());
        boolean configure = configurer(member);
        return new Overview(true, configure, maps(), mobs(), configure ? trackers() : List.of());
    }

    private List<MapView> maps() {
        Map<Integer, View> views = new HashMap<>();
        jdbc.query("SELECT * FROM resource_map_view", r -> {
            views.put(r.getInt("map_code"), new View(r.getInt("area_w"), r.getInt("area_h"), r.getInt("off_x"), r.getInt("off_y"),
                r.getInt("min_x"), r.getInt("max_z"), r.getDouble("scale_x"), r.getDouble("scale_y")));
        });
        return MAPS.entrySet().stream().map(e -> new MapView(e.getKey(), e.getValue(), views.get(e.getKey()))).toList();
    }

    private List<MobView> mobs() {
        return jdbc.query("""
            SELECT m.name,m.map_code,m.tier,m.first_seen_at,(SELECT count(*) FROM resource_spawn s WHERE s.mob_name=m.name) AS spawns
            FROM resource_mob m ORDER BY m.map_code, m.tier NULLS FIRST, m.name""",
            (r, n) -> new MobView(r.getString("name"), r.getInt("map_code"), (Integer) r.getObject("tier"),
                r.getLong("spawns"), r.getTimestamp("first_seen_at").toInstant()));
    }

    private List<Tracker> trackers() {
        return jdbc.query("SELECT m.id,m.nickname FROM resource_tracker t JOIN member m ON m.id=t.member_id ORDER BY lower(m.nickname)",
            (r, n) -> new Tracker(r.getLong("id"), r.getString("nickname")));
    }

    /** Todos os pontos do mapa (menos os de mobs ignorados), do mais antigo ao mais novo. */
    @Transactional(readOnly = true)
    public List<SpawnView> spawns(int map, MemberPrincipal actor) {
        if (!viewer(member(actor))) throw new AppException("Acesso negado", HttpStatus.FORBIDDEN);
        if (!MAPS.containsKey(map)) throw new AppException("Mapa sem recursos.", HttpStatus.BAD_REQUEST);
        return jdbc.query("""
            SELECT s.id,s.mob_name,m.tier,s.x,s.z,s.found_at,s.died_at,f.nickname FROM resource_spawn s
            JOIN resource_mob m ON m.name=s.mob_name LEFT JOIN member f ON f.id=s.found_by
            WHERE s.map_code=? AND coalesce(m.tier,-1)<>0 ORDER BY s.id""",
            (r, n) -> {
                Timestamp died = r.getTimestamp("died_at");
                return new SpawnView(r.getLong("id"), r.getString("mob_name"), (Integer) r.getObject("tier"), r.getInt("x"), r.getInt("z"),
                    r.getTimestamp("found_at").toInstant(), r.getString("nickname"), died == null ? null : died.toInstant());
            }, map);
    }

    /** Média por hora num mapa, pelas regras do servidor (spawn aleatório): grau 1, 2 e 3. */
    public static final Map<Integer, Double> EXPECTED_PER_HOUR = Map.of(1, 500.0, 2, 50.0, 3, 0.33);

    /**
     * byMinute: 12 faixas de 5 min do minuto da hora em que foi achado; byHour: 24 horas (Brasília).
     * perObservedHour = achados / horas em que alguém com a DLL achou qualquer recurso neste mapa.
     * nextLikelyAt = último + intervalo mediano entre achados seguidos (só uma estimativa).
     */
    public record TierStats(int tier, long total, double perObservedHour, Double expectedPerHour, Instant lastSeenAt,
                            Long medianGapSeconds, Instant nextLikelyAt, int[] byMinute, int[] byHour) {}
    public record Stats(int map, int days, long observedHours, List<TierStats> tiers) {}

    /** Estatística de respawn de um mapa nos últimos {@code days} dias (graus 1..3; desconhecidos e ignorados ficam de fora). */
    @Transactional(readOnly = true)
    public Stats stats(int map, int days, MemberPrincipal actor) {
        if (!viewer(member(actor))) throw new AppException("Acesso negado", HttpStatus.FORBIDDEN);
        if (!MAPS.containsKey(map)) throw new AppException("Mapa sem recursos.", HttpStatus.BAD_REQUEST);
        int span = Math.max(1, Math.min(days, 90));
        Timestamp since = Timestamp.from(clock.instant().minus(Duration.ofDays(span)));
        Long hours = jdbc.queryForObject(
            "SELECT count(DISTINCT date_trunc('hour', found_at)) FROM resource_spawn WHERE map_code=? AND found_at>?", Long.class, map, since);
        long observed = hours == null ? 0 : hours;
        List<TierStats> tiers = new ArrayList<>();
        for (int tier = 1; tier <= 3; tier++) {
            int t = tier;
            int[] byMinute = new int[12], byHour = new int[24];
            long[] total = {0};
            jdbc.query("""
                SELECT (extract(minute FROM s.found_at AT TIME ZONE 'America/Sao_Paulo')::int / 5) AS m5,
                       extract(hour FROM s.found_at AT TIME ZONE 'America/Sao_Paulo')::int AS h, count(*) AS n
                FROM resource_spawn s JOIN resource_mob m ON m.name=s.mob_name
                WHERE s.map_code=? AND m.tier=? AND s.found_at>? GROUP BY 1,2""",
                r -> { int n = r.getInt("n"); byMinute[r.getInt("m5")] += n; byHour[r.getInt("h")] += n; total[0] += n; },
                map, t, since);
            Timestamp last = jdbc.queryForObject("""
                SELECT max(s.found_at) FROM resource_spawn s JOIN resource_mob m ON m.name=s.mob_name
                WHERE s.map_code=? AND m.tier=?""", Timestamp.class, map, t);
            Double gap = jdbc.queryForObject("""
                SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY g) FROM (
                    SELECT extract(epoch FROM s.found_at - lag(s.found_at) OVER (ORDER BY s.found_at)) AS g
                    FROM resource_spawn s JOIN resource_mob m ON m.name=s.mob_name
                    WHERE s.map_code=? AND m.tier=? AND s.found_at>?) x WHERE g IS NOT NULL""", Double.class, map, t, since);
            Long medianGap = gap == null ? null : Math.round(gap);
            Instant lastAt = last == null ? null : last.toInstant();
            Instant next = lastAt == null || medianGap == null ? null : lastAt.plusSeconds(medianGap);
            tiers.add(new TierStats(t, total[0], observed == 0 ? 0 : (double) total[0] / observed, EXPECTED_PER_HOUR.get(t),
                lastAt, medianGap, next, byMinute, byHour));
        }
        return new Stats(map, span, observed, tiers);
    }

    private Member configurerOrThrow(MemberPrincipal actor) {
        Member member = member(actor);
        if (!configurer(member))
            throw new AppException("Apenas Administradores e o Líder mexem no mapa de recursos.", HttpStatus.FORBIDDEN);
        return member;
    }

    /** Administrador+: substitui a lista inteira de quem usa o mapa de recursos. */
    @Transactional
    public Overview updateAccess(List<Long> memberIds, MemberPrincipal actor) {
        Member member = configurerOrThrow(actor);
        var ids = new LinkedHashSet<>(memberIds == null ? List.<Long>of() : memberIds);
        if (ids.size() > 500) throw new AppException("Lista grande demais.", HttpStatus.BAD_REQUEST);
        for (Long id : ids)
            if (!members.existsById(id)) throw new AppException("Membro não encontrado", HttpStatus.NOT_FOUND);
        jdbc.update("DELETE FROM resource_tracker");
        for (Long id : ids) jdbc.update("INSERT INTO resource_tracker(member_id,added_by) VALUES(?,?)", id, member.getId());
        audit.log(AuditLogType.RESOURCE_ACCESS_UPDATED, member, member,
            Map.of("trackers", trackers().stream().map(Tracker::nickname).toList()));
        return overview(actor);
    }

    /** Administrador+: grau de um mob (1..3), 4 = Tower Rift Metal ou 0 = não é recurso. */
    @Transactional
    public Overview setTier(String name, int tier, MemberPrincipal actor) {
        Member member = configurerOrThrow(actor);
        if (tier < 0 || tier > 4) throw new AppException("Grau inválido.", HttpStatus.BAD_REQUEST);
        if (jdbc.update("UPDATE resource_mob SET tier=? WHERE name=?", tier, name) == 0)
            throw new AppException("Mob não encontrado", HttpStatus.NOT_FOUND);
        audit.log(AuditLogType.RESOURCE_MOB_UPDATED, member, member, Map.of("name", name, "tier", tier));
        return overview(actor);
    }
}
