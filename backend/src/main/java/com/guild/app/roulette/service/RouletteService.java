package com.guild.app.roulette.service;

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

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntUnaryOperator;

/**
 * Roleta no jogo: Admin/Líder escolhe os membros e escreve o prêmio (ou o motivo). O vencedor é sorteado aqui, na
 * hora de criar; a DLL (1.47.0+) busca em GET /api/roulettes/game e mostra a roleta no meio da tela de quem pode ver.
 * Todo mundo vê a mesma coisa porque a resposta traz quanto tempo passou desde a criação:
 * {@link #LEAD} de contagem, {@link #SPIN} girando e {@link #HOLD} mostrando o vencedor. Uma roleta por vez.
 */
@Service
@RequiredArgsConstructor
public class RouletteService {
    public static final Duration LEAD = Duration.ofSeconds(7);
    public static final Duration SPIN = Duration.ofSeconds(10);
    public static final Duration HOLD = Duration.ofSeconds(12);
    public static final Duration SHOWN = LEAD.plus(SPIN).plus(HOLD);
    public static final int MIN_ENTRIES = 2, MAX_ENTRIES = 40, MAX_PRIZE = 60;

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final AuditLogService audit;
    private Clock clock = Clock.systemUTC();
    private IntUnaryOperator draw = new SecureRandom()::nextInt;

    /** Testes: relógio fixo. */
    public void useClock(Clock value) { clock = value; }

    /** Testes: sorteio previsível (recebe o número de participantes, devolve a posição). */
    public void useDraw(Random random) { draw = random::nextInt; }

    public record NewRoulette(String prize, List<Long> memberIds, Boolean leadersOnly) {}
    /** winner fica null até a roleta parar (o site não estraga a surpresa). */
    public record Sent(long id, String prize, String createdBy, boolean leadersOnly, List<String> entries, String winner,
                       Instant createdAt, boolean spinning) {}
    /** Vazio para quem não é Admin/Líder. */
    public record View(boolean canCreate, List<Sent> recent) {}
    public record GameRoulette(long id, long elapsedMs, int winner, String prize, List<String> entries) {}

    // ---------------------------------------------------------------- site

    private static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.getDeletedAt() == null && m.getSuspendedAt() == null;
    }

    private static boolean creator(Member m) { return eligible(m) && m.getRole().isAtLeast(Role.ADMINISTRADOR); }

    private Member member(MemberPrincipal actor) {
        return members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
    }

    /** Uma linha só, sem TAB nem quebra (a DLL lê o prêmio como o último campo da linha). */
    static String cleanPrize(String prize) {
        if (prize == null) return "";
        return prize.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", " ").replaceAll("\\s+", " ").strip();
    }

    @Transactional(readOnly = true)
    public View view(MemberPrincipal actor) {
        if (!creator(member(actor))) return new View(false, List.of());
        return new View(true, recent());
    }

    private List<Sent> recent() {
        Instant now = clock.instant();
        record Row(long id, String prize, String by, boolean leadersOnly, int winner, Instant createdAt) {}
        List<Row> rows = jdbc.query("""
            SELECT r.*, m.nickname AS creator FROM roulette r LEFT JOIN member m ON m.id=r.created_by
            ORDER BY r.created_at DESC, r.id DESC LIMIT 15""",
            (r, n) -> new Row(r.getLong("id"), r.getString("prize"), r.getString("creator"), r.getBoolean("leaders_only"),
                r.getInt("winner_index"), r.getTimestamp("created_at").toInstant()));
        List<Sent> out = new ArrayList<>();
        for (Row row : rows) {
            List<String> entries = entries(row.id());
            boolean spinning = now.isBefore(row.createdAt().plus(LEAD).plus(SPIN));
            String winner = spinning || row.winner() >= entries.size() ? null : entries.get(row.winner());
            out.add(new Sent(row.id(), row.prize(), row.by(), row.leadersOnly(), entries, winner, row.createdAt(), spinning));
        }
        return out;
    }

    private List<String> entries(long rouletteId) {
        return jdbc.queryForList("SELECT nickname FROM roulette_entry WHERE roulette_id=? ORDER BY position", String.class, rouletteId);
    }

    @Transactional
    public View create(NewRoulette body, MemberPrincipal actor) {
        Member admin = member(actor);
        if (!creator(admin)) throw new AppException("Só Admin ou Líder cria roleta.", HttpStatus.FORBIDDEN);
        if (body == null) throw new AppException("Escolha os membros.", HttpStatus.BAD_REQUEST);
        String prize = cleanPrize(body.prize());
        if (prize.isEmpty()) throw new AppException("Escreva o prêmio ou o motivo da roleta.", HttpStatus.BAD_REQUEST);
        if (prize.length() > MAX_PRIZE) throw new AppException("Prêmio com no máximo " + MAX_PRIZE + " letras.", HttpStatus.BAD_REQUEST);
        LinkedHashSet<Long> ids = new LinkedHashSet<>(body.memberIds() == null ? List.of() : body.memberIds());
        ids.remove(null);
        if (ids.size() < MIN_ENTRIES) throw new AppException("Escolha pelo menos " + MIN_ENTRIES + " membros.", HttpStatus.BAD_REQUEST);
        if (ids.size() > MAX_ENTRIES) throw new AppException("No máximo " + MAX_ENTRIES + " membros na roleta.", HttpStatus.BAD_REQUEST);
        List<Member> entrants = new ArrayList<>();
        for (Long id : ids) {
            Member m = members.findById(id).filter(RouletteService::eligible)
                .orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
            entrants.add(m);
        }
        Instant now = clock.instant();
        // Uma por vez: a de agora ainda está na tela de alguém.
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM roulette WHERE created_at>?)",
                Boolean.class, Timestamp.from(now.minus(SHOWN)))))
            throw new AppException("Já tem uma roleta girando. Espere ela terminar.", HttpStatus.CONFLICT);
        boolean leadersOnly = !Boolean.FALSE.equals(body.leadersOnly());
        int winner = draw.applyAsInt(entrants.size());
        if (winner < 0 || winner >= entrants.size()) winner = 0;
        Long id = jdbc.queryForObject("""
            INSERT INTO roulette(created_by,prize,leaders_only,winner_index,created_at) VALUES(?,?,?,?,?) RETURNING id""",
            Long.class, admin.getId(), prize, leadersOnly, winner, Timestamp.from(now));
        for (int i = 0; i < entrants.size(); i++)
            jdbc.update("INSERT INTO roulette_entry(roulette_id,position,member_id,nickname) VALUES(?,?,?,?)",
                id, i, entrants.get(i).getId(), entrants.get(i).getNickname());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prize", prize);
        payload.put("winner", entrants.get(winner).getNickname());
        payload.put("entries", entrants.stream().map(Member::getNickname).toList());
        if (leadersOnly) payload.put("leadersOnly", true);
        audit.log(AuditLogType.ROULETTE_SPUN, admin, entrants.get(winner), payload);
        return new View(true, recent());
    }

    // ---------------------------------------------------------------- jogo

    /** A roleta na tela agora, se este personagem pode ver. */
    @Transactional(readOnly = true)
    public Optional<GameRoulette> current(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        Instant now = clock.instant();
        // A DLL de todo mundo consulta a cada 5 s: sem roleta na tela, nem procura o membro.
        record Row(long id, boolean leadersOnly, int winner, String prize, Instant createdAt) {}
        Optional<Row> row = jdbc.query("""
            SELECT id,leaders_only,winner_index,prize,created_at FROM roulette WHERE created_at>? AND created_at<=?
            ORDER BY created_at DESC, id DESC LIMIT 1""",
            (r, n) -> new Row(r.getLong(1), r.getBoolean(2), r.getInt(3), r.getString(4), r.getTimestamp(5).toInstant()),
            Timestamp.from(now.minus(SHOWN)), Timestamp.from(now)).stream().findFirst();
        if (row.isEmpty()) return Optional.empty();
        Optional<Member> member = members.findByGameCharacter(character.strip()).stream().filter(RouletteService::eligible).findFirst();
        if (member.isEmpty()) return Optional.empty();
        if (row.get().leadersOnly() && member.get().getRole() != Role.LIDER) return Optional.empty();
        List<String> entries = entries(row.get().id());
        if (row.get().winner() >= entries.size()) return Optional.empty();
        long elapsed = Duration.between(row.get().createdAt(), now).toMillis();
        return Optional.of(new GameRoulette(row.get().id(), Math.max(0, elapsed), row.get().winner(), row.get().prize(), entries));
    }

    /**
     * "id TAB decorrido TAB contagem TAB giro TAB vencedor na tela TAB vencedor TAB prêmio" (tempos em ms; vencedor
     * = posição, a partir de 0) e um nick por linha, na ordem da roleta.
     */
    public static String format(GameRoulette r) {
        StringBuilder out = new StringBuilder();
        out.append(r.id()).append('\t').append(r.elapsedMs()).append('\t').append(LEAD.toMillis()).append('\t')
            .append(SPIN.toMillis()).append('\t').append(HOLD.toMillis()).append('\t').append(r.winner()).append('\t')
            .append(cleanPrize(r.prize())).append('\n');
        for (String nick : r.entries()) out.append(nick.replaceAll("[\\t\\r\\n]", " ")).append('\n');
        return out.toString();
    }
}
