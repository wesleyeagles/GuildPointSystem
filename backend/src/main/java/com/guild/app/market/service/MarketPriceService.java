package com.guild.app.market.service;

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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Preço do market da Cerberus para o tooltip de item da DLL: menor preço e preço médio (por unidade, em CS),
 * separados por nível de upgrade, porque o mesmo item com talic vale muito mais. Cada código fica guardado
 * FRESH (3 min); dez pessoas passando o mouse no mesmo item = uma leitura do market. Se o market falhar,
 * devolve o último valor lido (até STALE) em vez de nada. Só responde a personagem de membro aprovado que está
 * na lista market_price_user; quem monta a lista é o Líder.
 */
@Service
@RequiredArgsConstructor
public class MarketPriceService {
    public static final Duration FRESH = Duration.ofMinutes(3), STALE = Duration.ofHours(1);
    public static final int CACHE_SIZE = 5000, FETCHES_PER_MINUTE = 40, REQUESTS_PER_MINUTE = 120;
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9]{3,11}");

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final MarketSource source;
    private final AuditLogService audit;
    private final Map<String, Entry> cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) { return size() > CACHE_SIZE; }
    });
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final Deque<Instant> fetches = new ArrayDeque<>();
    private final Map<Long, Deque<Instant>> requests = new HashMap<>();
    Clock clock = Clock.systemUTC();

    /** Preço de um nível de upgrade: por unidade, em CS. */
    public record Price(int upgrade, BigDecimal min, BigDecimal avg, int count) {}
    public record Prices(String code, Instant readAt, List<Price> byUpgrade) {}
    private record Entry(Instant readAt, List<Price> prices) {}
    public record Viewer(Long id, String nickname) {}
    /** Lista só vem para quem pode configurar (Líder). */
    public record AccessView(boolean canConfigure, List<Viewer> viewers) {}

    private static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.getDeletedAt() == null && m.getSuspendedAt() == null;
    }

    private static boolean configurer(Member m) { return eligible(m) && m.getRole() == Role.LIDER; }
    private boolean listed(Long memberId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM market_price_user WHERE member_id=?)", Boolean.class, memberId));
    }

    /** Membro aprovado da lista que usa este personagem. */
    @Transactional(readOnly = true)
    public Optional<Member> user(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        return members.findByGameCharacter(character.strip()).stream()
            .filter(MarketPriceService::eligible).filter(m -> listed(m.getId())).findFirst();
    }

    /** 403 personagem fora da lista, 400 código inválido, 429 pedidos demais, 503 market indisponível sem nada guardado. */
    public Prices prices(String character, String rawCode) {
        Member member = user(character).orElseThrow(() ->
            new AppException("Preço do market não liberado para este personagem.", HttpStatus.FORBIDDEN));
        String code = rawCode == null ? "" : rawCode.strip().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) throw new AppException("Código de item inválido.", HttpStatus.BAD_REQUEST);
        reserve(member.getId());
        Entry entry = cached(code, FRESH);
        if (entry == null) {
            synchronized (locks.computeIfAbsent(code, c -> new Object())) {
                entry = cached(code, FRESH);  // outro pedido pode ter lido enquanto este esperava
                if (entry == null) entry = fetch(code);
            }
        }
        if (entry == null) throw new AppException("Market da Cerberus indisponível agora.", HttpStatus.SERVICE_UNAVAILABLE);
        return new Prices(code, entry.readAt(), entry.prices());
    }

    private Entry cached(String code, Duration maxAge) {
        Entry e = cache.get(code);
        return e != null && e.readAt().plus(maxAge).isAfter(clock.instant()) ? e : null;
    }

    /** Lê do market; se não der (falha ou limite de leituras), fica com o último valor ainda aceitável. */
    private Entry fetch(String code) {
        if (!takeFetch()) return cached(code, STALE);
        Optional<List<MarketPage.Listing>> listings = source.listings(code);
        if (listings.isEmpty()) return cached(code, STALE);
        Entry entry = new Entry(clock.instant(), summarize(code, listings.get()));
        cache.put(code, entry);
        return entry;
    }

    /** Menor e média por unidade, por upgrade (só anúncios deste código), em ordem de upgrade. */
    static List<Price> summarize(String code, List<MarketPage.Listing> listings) {
        Map<Integer, List<BigDecimal>> byUpgrade = new TreeMap<>();
        for (MarketPage.Listing l : listings) {
            if (!code.equals(l.code()) || l.quantity() <= 0 || l.price() <= 0) continue;
            BigDecimal unit = BigDecimal.valueOf(l.price()).divide(BigDecimal.valueOf(l.quantity()), 2, RoundingMode.HALF_UP);
            byUpgrade.computeIfAbsent(l.upgrade(), u -> new ArrayList<>()).add(unit);
        }
        List<Price> out = new ArrayList<>();
        byUpgrade.forEach((upgrade, units) -> {
            BigDecimal min = units.stream().min(BigDecimal::compareTo).orElseThrow();
            BigDecimal sum = units.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(units.size()), 2, RoundingMode.HALF_UP);
            out.add(new Price(upgrade, min.stripTrailingZeros(), avg.stripTrailingZeros(), units.size()));
        });
        return out;
    }

    /** No máximo FETCHES_PER_MINUTE leituras do market por minuto, somando todo mundo. */
    private synchronized boolean takeFetch() {
        Instant now = clock.instant(), cutoff = now.minusSeconds(60);
        while (!fetches.isEmpty() && fetches.peekFirst().isBefore(cutoff)) fetches.pollFirst();
        if (fetches.size() >= FETCHES_PER_MINUTE) return false;
        fetches.addLast(now);
        return true;
    }

    private synchronized void reserve(Long memberId) {
        Instant now = clock.instant(), cutoff = now.minusSeconds(60);
        Deque<Instant> window = requests.computeIfAbsent(memberId, id -> new ArrayDeque<>());
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) window.pollFirst();
        if (window.size() >= REQUESTS_PER_MINUTE)
            throw new AppException("Muitos pedidos de preço; aguarde um pouco.", HttpStatus.TOO_MANY_REQUESTS);
        window.addLast(now);
    }

    @Transactional(readOnly = true)
    public AccessView view(MemberPrincipal actor) {
        Member member = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        return configurer(member) ? new AccessView(true, viewers()) : new AccessView(false, List.of());
    }

    private List<Viewer> viewers() {
        return jdbc.query("SELECT m.id,m.nickname FROM market_price_user u JOIN member m ON m.id=u.member_id ORDER BY lower(m.nickname)",
            (r, n) -> new Viewer(r.getLong("id"), r.getString("nickname")));
    }

    /** Líder: substitui a lista inteira de quem vê o preço do market no tooltip. */
    @Transactional
    public AccessView update(List<Long> memberIds, MemberPrincipal actor) {
        Member member = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!configurer(member)) throw new AppException("Só o Líder libera o preço do market.", HttpStatus.FORBIDDEN);
        var ids = new LinkedHashSet<>(memberIds == null ? List.<Long>of() : memberIds);
        if (ids.size() > 500) throw new AppException("Lista grande demais.", HttpStatus.BAD_REQUEST);
        for (Long id : ids)
            if (!members.existsById(id)) throw new AppException("Membro não encontrado", HttpStatus.NOT_FOUND);
        jdbc.update("DELETE FROM market_price_user");
        for (Long id : ids) jdbc.update("INSERT INTO market_price_user(member_id,added_by) VALUES(?,?)", id, member.getId());
        List<Viewer> now = viewers();
        audit.log(AuditLogType.MARKET_PRICE_ACCESS_UPDATED, member, member,
            Map.of("viewers", now.stream().map(Viewer::nickname).toList()));
        return new AccessView(true, now);
    }

    /** Esquece os preços guardados (testes). */
    public void forgetCache() { cache.clear(); }
}
