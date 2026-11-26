package com.guild.app.enemy.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Radar de guilds inimigas. O Admin/Líder cadastra o nome exato das guilds no site; a DLL de cada membro recebe
 * essa lista junto do status da raid/guild (RaidLiveService, fx=6), lê o nome da guild de quem está por perto e
 * manda de volta quem é de uma guild da lista. O site junta o que todos viram e devolve os pontos vermelhos para
 * todas as DLLs. As posições ficam só em memória.
 */
@Service
public class EnemyRadarService {
    /** Guilds na lista, no máximo. */
    public static final int MAX_GUILDS = 30;
    /** Inimigos por envio de uma DLL, no máximo (o resto é ignorado). */
    public static final int MAX_PER_REPORT = 100;
    /** Ponto vermelho no mapa do jogo: visto há no máximo isso. */
    public static final Duration GAME_FRESH = Duration.ofSeconds(15);
    /** Lista "vistos agora" do site: some depois disso. */
    public static final Duration SITE_KEEP = Duration.ofMinutes(10);
    private static final Pattern NAME = Pattern.compile("[\\x21-\\x7e]{1,16}");

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final CharacterStatusRepository statuses;
    private final AuditLogService audit;
    Clock clock = Clock.systemUTC();

    public EnemyRadarService(JdbcTemplate jdbc, MemberRepository members, CharacterStatusRepository statuses,
                             AuditLogService audit) {
        this.jdbc = jdbc;
        this.members = members;
        this.statuses = statuses;
        this.audit = audit;
    }

    /** Posição como o mapa do jogo usa: índice do mapa e X/Z no mundo. */
    public record Spot(int map, int x, int z) {}
    /** Um inimigo que a DLL viu: nick e guild como o jogo escreve, X/Z no mundo (o mapa é o de quem viu). */
    public record Seen(String nick, String guild, int x, int z) {}
    private record Sighting(String nick, String guild, Spot spot, Instant at, long reporterId, String reporter) {}

    public record EnemyGuild(long id, String name, Instant createdAt) {}
    /** Para o site: onde = mapa/região de quem viu (último envio de local da DLL dele). */
    public record SightingView(String nick, String guild, String map, String region, String seenBy, long ageSeconds) {}
    public record View(boolean canConfigure, List<EnemyGuild> guilds, List<SightingView> sightings) {}
    public record NewGuild(String name) {}

    private final Map<String, Sighting> sightings = new ConcurrentHashMap<>();
    private volatile List<String> names;

    // ---------------------------------------------------------------- DLL

    /** Nomes das guilds na lista (cache; recarrega quando o site muda a lista). */
    public List<String> guildNames() {
        var current = names;
        if (current != null) return current;
        synchronized (this) {
            if (names == null) names = List.copyOf(jdbc.queryForList("SELECT name FROM enemy_guild ORDER BY name", String.class));
            return names;
        }
    }

    /**
     * Guarda o que a DLL de um membro viu. Só entra quem é de uma guild da lista (o nome tem que bater certinho);
     * sem posição de quem viu, não há em que mapa pôr o ponto, e o envio é ignorado.
     */
    public void report(Member reporter, Spot here, List<Seen> seen) {
        if (here == null || seen.isEmpty()) return;
        var watched = new HashSet<>(guildNames());
        if (watched.isEmpty()) return;
        Instant now = clock.instant();
        int taken = 0;
        for (Seen s : seen) {
            if (taken++ >= MAX_PER_REPORT) break;
            if (!watched.contains(s.guild())) continue;
            sightings.put(s.nick().toLowerCase(Locale.ROOT),
                new Sighting(s.nick(), s.guild(), new Spot(here.map(), s.x(), s.z()), now, reporter.getId(), reporter.getNickname()));
        }
    }

    /**
     * Linhas para a DLL (fx=6): "W<TAB>guild" por guild da lista e "X<TAB>nick<TAB>guild<TAB>mapa,x,z<TAB>idade"
     * por inimigo visto nos últimos {@link #GAME_FRESH}.
     */
    public String gameLines() {
        var out = new StringBuilder();
        var watched = guildNames();
        for (String name : watched) out.append("W\t").append(name).append('\n');
        if (watched.isEmpty()) return out.toString();
        Instant now = clock.instant();
        for (Sighting s : sightings.values()) {
            long age = Duration.between(s.at(), now).toSeconds();
            if (age < 0 || age > GAME_FRESH.toSeconds() || !watched.contains(s.guild())) continue;
            out.append("X\t").append(s.nick()).append('\t').append(s.guild()).append('\t')
                .append(s.spot().map()).append(',').append(s.spot().x()).append(',').append(s.spot().z())
                .append('\t').append(age).append('\n');
        }
        return out.toString();
    }

    @Scheduled(fixedDelay = 60_000)
    public void prune() {
        Instant limit = clock.instant().minus(SITE_KEEP);
        sightings.values().removeIf(s -> s.at().isBefore(limit));
    }

    // ---------------------------------------------------------------- site

    private static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.getDeletedAt() == null && m.getSuspendedAt() == null;
    }

    private Member member(MemberPrincipal actor) {
        Member m = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!eligible(m)) throw new AppException("Acesso negado", HttpStatus.FORBIDDEN);
        return m;
    }

    private Member configurer(MemberPrincipal actor) {
        Member m = member(actor);
        if (!m.getRole().isAtLeast(Role.ADMINISTRADOR)) throw new AppException("Só Admin ou Líder mexe nas guilds inimigas.", HttpStatus.FORBIDDEN);
        return m;
    }

    /** Todo membro aprovado vê a lista e quem foi visto; só Admin/Líder edita. */
    @Transactional(readOnly = true)
    public View view(MemberPrincipal actor) {
        Member m = member(actor);
        return view(m.getRole().isAtLeast(Role.ADMINISTRADOR));
    }

    private View view(boolean canConfigure) {
        var guilds = jdbc.query("SELECT id, name, created_at FROM enemy_guild ORDER BY lower(name), name",
            (r, n) -> new EnemyGuild(r.getLong(1), r.getString(2), r.getTimestamp(3).toInstant()));
        return new View(canConfigure, guilds, sightingViews());
    }

    private List<SightingView> sightingViews() {
        Instant now = clock.instant();
        var watched = new HashSet<>(guildNames());
        var where = new HashMap<Long, String[]>();
        return sightings.values().stream()
            .filter(s -> watched.contains(s.guild()) && !s.at().isBefore(now.minus(SITE_KEEP)))
            .sorted(Comparator.comparing(Sighting::at).reversed())
            .map(s -> {
                String[] place = where.computeIfAbsent(s.reporterId(), id -> statuses.findFirstByMemberIdOrderByUpdatedAtDesc(id)
                    .map(c -> new String[]{c.getMapName(), c.getRegionName()}).orElse(new String[]{null, null}));
                return new SightingView(s.nick(), s.guild(), place[0], place[1], s.reporter(),
                    Math.max(0, Duration.between(s.at(), now).toSeconds()));
            })
            .toList();
    }

    @Transactional
    public View add(NewGuild body, MemberPrincipal actor) {
        Member admin = configurer(actor);
        String name = body == null || body.name() == null ? "" : body.name().strip();
        if (!NAME.matcher(name).matches())
            throw new AppException("Nome da guild: até 16 letras, números ou símbolos, sem espaço.", HttpStatus.BAD_REQUEST);
        Integer total = jdbc.queryForObject("SELECT count(*) FROM enemy_guild", Integer.class);
        if (total != null && total >= MAX_GUILDS) throw new AppException("Lista de guilds inimigas cheia.", HttpStatus.BAD_REQUEST);
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM enemy_guild WHERE name=?)", Boolean.class, name));
        if (exists) throw new AppException("Essa guild já está na lista.", HttpStatus.CONFLICT);
        jdbc.update("INSERT INTO enemy_guild(name, created_by) VALUES(?, ?)", name, admin.getId());
        names = null;
        audit.log(AuditLogType.ENEMY_GUILD_UPDATED, admin, null, Map.of("guild", name));
        return view(true);
    }

    @Transactional
    public View remove(long id, MemberPrincipal actor) {
        Member admin = configurer(actor);
        String name = jdbc.query("SELECT name FROM enemy_guild WHERE id=?", (r, n) -> r.getString(1), id).stream().findFirst()
            .orElseThrow(() -> new AppException("Guild não encontrada.", HttpStatus.NOT_FOUND));
        jdbc.update("DELETE FROM enemy_guild WHERE id=?", id);
        names = null;
        sightings.values().removeIf(s -> s.guild().equals(name));
        audit.log(AuditLogType.ENEMY_GUILD_UPDATED, admin, null, Map.of("guild", name, "removed", true));
        return view(true);
    }
}
