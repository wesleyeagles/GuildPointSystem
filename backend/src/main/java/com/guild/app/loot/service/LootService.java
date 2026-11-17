package com.guild.app.loot.service;

import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loot dos membros, lido pela DLL (1.49.0+) no chat do sistema do jogo. Cada membro com a DLL manda as linhas que
 * viu; o site guarda só o loot de membro aprovado. O anúncio do servidor ("has acquired") chega de todo mundo que
 * está online, então a mesma linha de vários membros vira uma só: dentro de {@link #MERGE}, uma linha já gravada
 * que este membro ainda não contou é a mesma. Dois drops iguais vistos pelo mesmo membro continuam dois.
 * Consulta de Administrador+ (Admin → Loot). Guardado por {@link #KEEP}.
 */
@Service
@RequiredArgsConstructor
public class LootService {
    public static final Duration MERGE = Duration.ofMinutes(2);
    public static final Duration KEEP = Duration.ofDays(90);
    public static final int MAX_LINES = 50, MAX_LINE = 300, MAX_ITEM = 120;

    public enum Kind { DROP, PARTY, REWARD }

    private static final Pattern PREFIX = Pattern.compile("^\\[\\d+]\\s*:\\s*");
    private static final Pattern ACQUIRED = Pattern.compile("^(\\S{1,32}) has acquired (.+?)\\.?$");
    private static final Pattern OBTAINED = Pattern.compile("^(\\S{1,32}) has obtained (\\d{1,6}) (.+?)\\.?$");
    private static final Pattern REWARD = Pattern.compile("^Compensation item for (\\S{1,32}) -\\s*(.+?)\\*?$");

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private Clock clock = Clock.systemUTC();

    /** Testes: relógio fixo. */
    public void useClock(Clock value) { clock = value; }

    public record Loot(String looter, Kind kind, String item, int quantity) {}

    /** Uma linha do chat do sistema; vazio se não for loot. */
    public static Optional<Loot> parse(String line) {
        if (line == null) return Optional.empty();
        String text = line.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
        text = PREFIX.matcher(text).replaceFirst("");
        Matcher m = ACQUIRED.matcher(text);
        if (m.matches()) return loot(m.group(1), Kind.DROP, m.group(2), 1);
        m = OBTAINED.matcher(text);
        if (m.matches()) return loot(m.group(1), Kind.PARTY, m.group(3), Integer.parseInt(m.group(2)));
        m = REWARD.matcher(text);
        if (m.matches()) return loot(m.group(1), Kind.REWARD, m.group(2), 1);
        return Optional.empty();
    }

    private static Optional<Loot> loot(String looter, Kind kind, String item, int quantity) {
        String name = item.strip();
        if (name.isEmpty() || quantity <= 0) return Optional.empty();
        if (name.length() > MAX_ITEM) name = name.substring(0, MAX_ITEM);
        return Optional.of(new Loot(looter, kind, name, quantity));
    }

    private static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.getDeletedAt() == null;
    }

    private Optional<Member> member(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        return members.findByGameCharacter(character.strip()).stream().filter(LootService::eligible).findFirst();
    }

    /**
     * Linhas mandadas pela DLL de {@code character} (uma por linha). Devolve quantas viraram loot novo; 403 se o
     * personagem não é de membro aprovado.
     */
    @Transactional
    public int ingest(String character, String body) {
        Member reporter = member(character)
            .orElseThrow(() -> new AppException("Personagem sem conta aprovada.", HttpStatus.FORBIDDEN));
        if (body == null || body.isBlank()) return 0;
        Instant now = clock.instant();
        Map<String, Optional<Member>> looters = new HashMap<>();
        int added = 0, lines = 0;
        for (String line : body.split("\n")) {
            if (++lines > MAX_LINES) break;
            if (line.length() > MAX_LINE) continue;
            Optional<Loot> parsed = parse(line);
            if (parsed.isEmpty()) continue;
            Loot loot = parsed.get();
            Optional<Member> looter = looters.computeIfAbsent(loot.looter().toLowerCase(Locale.ROOT), k -> member(loot.looter()));
            if (looter.isEmpty()) continue;  // não é membro
            if (record(looter.get().getId(), loot, reporter.getId(), now)) added++;
        }
        return added;
    }

    private boolean record(long memberId, Loot loot, long reporterId, Instant now) {
        // Um por vez por membro: dois envios ao mesmo tempo não gravam o mesmo drop duas vezes.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext('member_loot'), ?)", rs -> {}, (int) memberId);
        List<Long> same = jdbc.queryForList("""
            SELECT l.id FROM member_loot l
            WHERE l.member_id=? AND l.kind=? AND l.item=? AND l.quantity=? AND l.looted_at>?
              AND NOT EXISTS(SELECT 1 FROM member_loot_report r WHERE r.loot_id=l.id AND r.reporter_id=?)
            ORDER BY l.looted_at, l.id LIMIT 1""", Long.class,
            memberId, loot.kind().name(), loot.item(), loot.quantity(), Timestamp.from(now.minus(MERGE)), reporterId);
        if (!same.isEmpty()) {
            jdbc.update("INSERT INTO member_loot_report(loot_id,reporter_id) VALUES(?,?)", same.get(0), reporterId);
            return false;
        }
        Long id = jdbc.queryForObject("""
            INSERT INTO member_loot(member_id,looter,kind,item,quantity,looted_at,reported_by) VALUES(?,?,?,?,?,?,?)
            RETURNING id""", Long.class,
            memberId, loot.looter(), loot.kind().name(), loot.item(), loot.quantity(), Timestamp.from(now), reporterId);
        jdbc.update("INSERT INTO member_loot_report(loot_id,reporter_id) VALUES(?,?)", id, reporterId);
        return true;
    }

    /** seenBy = quantos membros com a DLL viram a mesma linha. */
    public record Row(long id, long memberId, String nickname, String looter, Kind kind, String item, int quantity,
                      Instant lootedAt, int seenBy) {}

    /** Administrador+: loot dos últimos {@code days} dias, mais novo primeiro (até 1000 linhas). */
    @Transactional(readOnly = true)
    public List<Row> query(String nickname, String item, String kind, int days) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        Instant since = clock.instant().minus(Duration.ofDays(Math.max(1, Math.min(days, KEEP.toDays()))));
        StringBuilder sql = new StringBuilder("""
            SELECT l.id,l.member_id,m.nickname,l.looter,l.kind,l.item,l.quantity,l.looted_at,
                   (SELECT count(*) FROM member_loot_report r WHERE r.loot_id=l.id) AS seen
            FROM member_loot l JOIN member m ON m.id=l.member_id WHERE l.looted_at>?""");
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(since)));
        if (nickname != null && !nickname.isBlank()) {
            sql.append(" AND (m.nickname ILIKE ? OR l.looter ILIKE ?)");
            String like = "%" + like(nickname) + "%";
            args.add(like);
            args.add(like);
        }
        if (item != null && !item.isBlank()) {
            sql.append(" AND l.item ILIKE ?");
            args.add("%" + like(item) + "%");
        }
        if (kind != null && !kind.isBlank()) {
            Kind k;
            try { k = Kind.valueOf(kind.strip().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw new AppException("Tipo inválido.", HttpStatus.BAD_REQUEST); }
            sql.append(" AND l.kind=?");
            args.add(k.name());
        }
        sql.append(" ORDER BY l.looted_at DESC, l.id DESC LIMIT 1000");
        return jdbc.query(sql.toString(), (r, n) -> new Row(r.getLong("id"), r.getLong("member_id"), r.getString("nickname"),
            r.getString("looter"), Kind.valueOf(r.getString("kind")), r.getString("item"), r.getInt("quantity"),
            r.getTimestamp("looted_at").toInstant(), r.getInt("seen")), args.toArray());
    }

    private static String like(String text) {
        return text.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Todo dia às 04:10: apaga o que passou de {@link #KEEP}. */
    @Scheduled(cron = "0 10 4 * * *")
    @Transactional
    public void prune() {
        jdbc.update("DELETE FROM member_loot WHERE looted_at<?", Timestamp.from(clock.instant().minus(KEEP)));
    }
}
