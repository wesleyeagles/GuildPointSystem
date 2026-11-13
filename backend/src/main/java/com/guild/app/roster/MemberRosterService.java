package com.guild.app.roster;

import com.guild.app.characterstatus.entity.CharacterStatus;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.shout.service.ShoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Lista de membros no jogo (DLL, botao so de Lider/Administrador): todos os membros aprovados do site,
 * das duas guildas, com online, mapa, regiao, tempo parado, ultima vez visto e ultima vez que ganhou EXP.
 * Mesma regra de quem pode dar shout (personagem de Lider/Administrador aprovado).
 */
@Service
@RequiredArgsConstructor
public class MemberRosterService {

    /** A DLL reenvia o status a cada 60 s com o jogo aberto: 3 min sem envio = fora do jogo. */
    static final Duration ONLINE = Duration.ofMinutes(3);
    /** Varios lideres com o painel aberto dividem a mesma conta. */
    static final Duration CACHE = Duration.ofSeconds(5);

    private final MemberRepository members;
    private final CharacterStatusRepository statuses;
    private final ShoutService shoutService;
    Clock clock = Clock.systemUTC();

    private record Cached(Instant at, List<Row> rows) {}
    private volatile Cached cached;

    /** Uma linha por membro; tempos em segundos ate agora (-1 = nunca visto / sem dado). */
    record Row(String nick, boolean online, String map, String region, long stillSeconds, long seenSeconds, long expSeconds) {
        String line() {
            return clean(nick) + "\t" + (online ? 1 : 0) + "\t" + clean(map) + "\t" + clean(region) + "\t"
                    + stillSeconds + "\t" + seenSeconds + "\t" + expSeconds + "\n";
        }
    }

    @Transactional(readOnly = true)
    public String forGame(String character) {
        if (shoutService.sender(character).isEmpty()) {
            throw new AppException("Só o Líder e os Administradores veem a lista de membros.", HttpStatus.FORBIDDEN);
        }
        var sb = new StringBuilder();
        for (Row row : rows()) sb.append(row.line());
        return sb.toString();
    }

    List<Row> rows() {
        Instant now = clock.instant();
        Cached c = cached;
        if (c != null && c.at().plus(CACHE).isAfter(now)) return c.rows();
        List<Row> rows = build(now);
        cached = new Cached(now, rows);
        return rows;
    }

    private List<Row> build(Instant now) {
        Map<Long, List<CharacterStatus>> byMember = new HashMap<>();
        for (CharacterStatus s : statuses.findAll()) {
            byMember.computeIfAbsent(s.getMember().getId(), k -> new ArrayList<>()).add(s);
        }
        var rows = new ArrayList<Row>();
        for (Member m : members.findByStatusOrderByPointsDesc(MemberStatus.APROVADO)) {
            List<CharacterStatus> chars = byMember.getOrDefault(m.getId(), List.of());
            // Personagem jogado agora (ou por ultimo): o envio mais recente.
            CharacterStatus last = chars.stream().max(Comparator.comparing(CharacterStatus::getObservedAt)).orElse(null);
            Instant expAt = chars.stream().map(CharacterStatus::getExpGainedAt).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            boolean online = last != null && last.getObservedAt().isAfter(now.minus(ONLINE));
            boolean located = online && last.getLocationAt() != null && last.getLocationAt().isAfter(now.minus(ONLINE));
            // Parado = mesma coordenada e sem EXP; sem coordenada (DLL antiga) fica -1 e nunca vira AFK.
            Instant still = located ? last.idleSince() : null;
            rows.add(new Row(m.getNickname(), online,
                    located ? last.getMapName() : null,
                    located ? last.getRegionName() : null,
                    ago(still, now), last == null ? -1 : ago(last.getObservedAt(), now), ago(expAt, now)));
        }
        // Online primeiro (por nome); depois quem foi visto ha menos tempo; nunca visto por ultimo.
        rows.sort(ORDER);
        // Mesmo nick em duas contas (conta compartilhada): uma linha so, a primeira na ordem (online / mais recente).
        var seen = new java.util.HashSet<String>();
        rows.removeIf(r -> !seen.add(r.nick().toLowerCase(java.util.Locale.ROOT)));
        return List.copyOf(rows);
    }

    private static final Comparator<Row> ORDER = Comparator.comparing((Row r) -> !r.online())
                .thenComparingLong(r -> r.online() ? 0 : (r.seenSeconds() < 0 ? Long.MAX_VALUE : r.seenSeconds()))
                .thenComparing(Row::nick, String.CASE_INSENSITIVE_ORDER);

    private static long ago(Instant at, Instant now) {
        return at == null ? -1 : Math.max(0, Duration.between(at, now).getSeconds());
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
