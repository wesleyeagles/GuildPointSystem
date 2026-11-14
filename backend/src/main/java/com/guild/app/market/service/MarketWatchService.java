package com.guild.app.market.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Alerta de item no market da Cerberus: o Admin/Líder monta a lista "membro quer o item X" (upgrade mínimo e
 * preço máximo por unidade são opcionais). A cada {@code guild.market.watch-delay} o site lê no market só os
 * códigos dessa lista; anúncio novo que serve vira um alerta para aquele membro, e a DLL dele mostra o card no
 * jogo (GET /api/market/game/alerts). Um anúncio só avisa uma vez enquanto continua à venda: se sair e voltar
 * (novo anúncio), avisa de novo.
 */
@Slf4j
@Service
public class MarketWatchService {
    /** Lidos por rodada, no máximo: o resto fica para a próxima (fila circular). */
    public static final int CODES_PER_SCAN = 30;
    /** Alertas por linha numa rodada (o item recém-adicionado pode ter dezenas à venda): os mais baratos. */
    public static final int ALERTS_PER_WATCH = 3;
    public static final int MAX_WATCHES = 300;
    /** O card fica no jogo por GAME_WINDOW (depois o anúncio provavelmente já foi vendido); o banco guarda um dia. */
    public static final Duration GAME_WINDOW = Duration.ofMinutes(30), KEEP = Duration.ofDays(1);
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9]{3,11}");

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final MarketSource source;
    private final AuditLogService audit;
    private final boolean enabled;
    private int cursor;
    Clock clock = Clock.systemUTC();

    public MarketWatchService(JdbcTemplate jdbc, MemberRepository members, MarketSource source, AuditLogService audit,
                              @Value("${guild.market.watch-enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.members = members;
        this.source = source;
        this.audit = audit;
        this.enabled = enabled;
    }

    public record Watch(long id, long memberId, String nickname, String itemCode, String itemName,
                        Integer minUpgrade, Long maxPrice, Instant createdAt) {}
    public record Alert(long id, String nickname, String itemCode, String itemName, int upgrade, int quantity,
                        long price, Instant createdAt) {}
    /** Tudo vazio para quem não é Admin/Líder. */
    public record View(boolean canConfigure, List<Watch> watches, List<Alert> recentAlerts) {}
    public record NewWatch(Long memberId, String itemCode, String itemName, Integer minUpgrade, Long maxPrice) {}
    public record CatalogItem(String code, String name) {}

    private static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.getDeletedAt() == null && m.getSuspendedAt() == null;
    }

    private static boolean configurer(Member m) { return eligible(m) && m.getRole().isAtLeast(Role.ADMINISTRADOR); }

    private Member configurer(MemberPrincipal actor) {
        Member m = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!configurer(m)) throw new AppException("Só Admin ou Líder mexe nos alertas do market.", HttpStatus.FORBIDDEN);
        return m;
    }

    // ---------------------------------------------------------------- site

    @Transactional(readOnly = true)
    public View view(MemberPrincipal actor) {
        Member m = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!configurer(m)) return new View(false, List.of(), List.of());
        return new View(true, watches(), recentAlerts());
    }

    private List<Watch> watches() {
        return jdbc.query("""
            SELECT w.*, m.nickname FROM market_watch w JOIN member m ON m.id=w.member_id
            ORDER BY lower(m.nickname), lower(w.item_name), w.id""",
            (r, n) -> new Watch(r.getLong("id"), r.getLong("member_id"), r.getString("nickname"), r.getString("item_code"),
                r.getString("item_name"), (Integer) r.getObject("min_upgrade", Integer.class),
                (Long) r.getObject("max_price", Long.class), r.getTimestamp("created_at").toInstant()));
    }

    private List<Alert> recentAlerts() {
        return jdbc.query("""
            SELECT a.*, m.nickname FROM market_alert a JOIN member m ON m.id=a.member_id
            ORDER BY a.created_at DESC, a.id DESC LIMIT 30""",
            (r, n) -> new Alert(r.getLong("id"), r.getString("nickname"), r.getString("item_code"), r.getString("item_name"),
                r.getInt("upgrade"), r.getInt("quantity"), r.getLong("price"), r.getTimestamp("created_at").toInstant()));
    }

    @Transactional
    public View add(NewWatch body, MemberPrincipal actor) {
        Member admin = configurer(actor);
        if (body == null || body.memberId() == null) throw new AppException("Escolha o membro.", HttpStatus.BAD_REQUEST);
        Member target = members.findById(body.memberId()).filter(MarketWatchService::eligible)
            .orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        String code = body.itemCode() == null ? "" : body.itemCode().strip().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) throw new AppException("Código de item inválido.", HttpStatus.BAD_REQUEST);
        String name = body.itemName() == null ? "" : body.itemName().replaceAll("\\p{Cntrl}+", " ").strip();
        if (name.isEmpty()) name = jdbc.query("SELECT name FROM game_lua_item WHERE game_code=? LIMIT 1",
            (r, n) -> r.getString(1), code).stream().findFirst().orElse(code);
        if (name.length() > 200) name = name.substring(0, 200);
        Integer minUpgrade = body.minUpgrade() == null || body.minUpgrade() == 0 ? null : body.minUpgrade();
        if (minUpgrade != null && (minUpgrade < 0 || minUpgrade > 7)) throw new AppException("Upgrade entre 0 e 7.", HttpStatus.BAD_REQUEST);
        Long maxPrice = body.maxPrice();
        if (maxPrice != null && maxPrice <= 0) throw new AppException("Preço máximo precisa ser maior que zero.", HttpStatus.BAD_REQUEST);
        Integer total = jdbc.queryForObject("SELECT count(*) FROM market_watch", Integer.class);
        if (total != null && total >= MAX_WATCHES) throw new AppException("Lista de alertas cheia.", HttpStatus.BAD_REQUEST);
        boolean duplicate = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM market_watch WHERE member_id=? AND item_code=?
              AND min_upgrade IS NOT DISTINCT FROM ? AND max_price IS NOT DISTINCT FROM ?)""",
            Boolean.class, target.getId(), code, minUpgrade, maxPrice));
        if (duplicate) throw new AppException("Esse alerta já existe.", HttpStatus.CONFLICT);
        jdbc.update("INSERT INTO market_watch(member_id,item_code,item_name,min_upgrade,max_price,created_by) VALUES(?,?,?,?,?,?)",
            target.getId(), code, name, minUpgrade, maxPrice, admin.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("item", name);
        payload.put("code", code);
        if (minUpgrade != null) payload.put("minUpgrade", minUpgrade);
        if (maxPrice != null) payload.put("maxPrice", maxPrice);
        audit.log(AuditLogType.MARKET_WATCH_UPDATED, admin, target, payload);
        return new View(true, watches(), recentAlerts());
    }

    @Transactional
    public View remove(long id, MemberPrincipal actor) {
        Member admin = configurer(actor);
        var row = jdbc.query("SELECT member_id,item_name,item_code FROM market_watch WHERE id=?",
            (r, n) -> new Object[]{r.getLong(1), r.getString(2), r.getString(3)}, id).stream().findFirst()
            .orElseThrow(() -> new AppException("Alerta não encontrado.", HttpStatus.NOT_FOUND));
        jdbc.update("DELETE FROM market_watch WHERE id=?", id);
        audit.log(AuditLogType.MARKET_WATCH_UPDATED, admin, members.getReferenceById((Long) row[0]),
            Map.of("item", row[1], "code", row[2], "removed", true));
        return new View(true, watches(), recentAlerts());
    }

    /** Busca no catálogo do jogo (nome ou código) para escolher o item. */
    @Transactional(readOnly = true)
    public List<CatalogItem> catalog(String search, MemberPrincipal actor) {
        configurer(actor);
        String term = search == null ? "" : search.strip();
        if (term.length() < 2) return List.of();
        String like = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return jdbc.query("""
            SELECT game_code, name FROM game_lua_item
            WHERE (name ILIKE ? ESCAPE '!' OR game_code ILIKE ? ESCAPE '!') AND game_code ~ '^[a-z][a-z0-9]{3,11}$'
            ORDER BY (lower(name)=lower(?)) DESC, length(name), name LIMIT 20""",
            (r, n) -> new CatalogItem(r.getString(1), r.getString(2)), like, like, term);
    }

    // ---------------------------------------------------------------- jogo

    /** Alertas recentes do membro que usa este personagem (só aprovado). */
    @Transactional(readOnly = true)
    public List<Alert> gameAlerts(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return List.of();
        Timestamp since = Timestamp.from(clock.instant().minus(GAME_WINDOW));
        // A DLL de todo mundo consulta a cada 5 s: sem alerta recente nenhum, nem procura o membro.
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM market_alert WHERE created_at>?)", Boolean.class, since)))
            return List.of();
        Optional<Member> member = members.findByGameCharacter(character.strip()).stream().filter(MarketWatchService::eligible).findFirst();
        if (member.isEmpty()) return List.of();
        return jdbc.query("""
            SELECT a.*, '' AS nickname FROM market_alert a WHERE a.member_id=? AND a.created_at>?
            ORDER BY a.created_at DESC, a.id DESC LIMIT 20""",
            (r, n) -> new Alert(r.getLong("id"), "", r.getString("item_code"), r.getString("item_name"),
                r.getInt("upgrade"), r.getInt("quantity"), r.getLong("price"), r.getTimestamp("created_at").toInstant()),
            member.get().getId(), since);
    }

    // ---------------------------------------------------------------- leitura do market

    @Scheduled(initialDelayString = "${guild.market.watch-initial-delay:PT1M}", fixedDelayString = "${guild.market.watch-delay:PT2M}")
    public void scheduledScan() {
        if (!enabled) return;
        try {
            scan();
        } catch (Exception e) {
            log.warn("Alertas do market: rodada falhou: {}", e.getMessage());
        }
    }

    /** Uma rodada: lê até CODES_PER_SCAN códigos da lista e grava os alertas novos. Devolve quantos alertas. */
    public synchronized int scan() {
        jdbc.update("DELETE FROM market_alert WHERE created_at<?", Timestamp.from(clock.instant().minus(KEEP)));
        List<String> codes = jdbc.queryForList("SELECT DISTINCT item_code FROM market_watch ORDER BY item_code", String.class);
        if (codes.isEmpty()) return 0;
        int created = 0, n = Math.min(CODES_PER_SCAN, codes.size());
        int start = cursor % codes.size();
        for (int i = 0; i < n; i++) {
            String code = codes.get((start + i) % codes.size());
            Optional<List<MarketPage.Listing>> listings = source.listings(code);
            if (listings.isEmpty()) continue;   // market fora: não mexe no que já foi visto
            created += apply(code, listings.get());
        }
        cursor = (start + n) % codes.size();
        return created;
    }

    private record WatchRow(long id, long memberId, String name, Integer minUpgrade, Long maxPrice) {}

    /** Confere os anúncios de um código contra as linhas que querem esse item. */
    int apply(String code, List<MarketPage.Listing> all) {
        List<MarketPage.Listing> listings = new ArrayList<>(all.stream().filter(l -> code.equals(l.code()) && l.quantity() > 0 && l.price() > 0).toList());
        listings.sort(Comparator.comparingDouble(MarketWatchService::unit));
        Map<MarketPage.Listing, String> keys = keys(listings);
        List<WatchRow> watches = jdbc.query("SELECT id,member_id,item_name,min_upgrade,max_price FROM market_watch WHERE item_code=?",
            (r, n) -> new WatchRow(r.getLong(1), r.getLong(2), r.getString(3), (Integer) r.getObject(4, Integer.class),
                (Long) r.getObject(5, Long.class)), code);
        int created = 0;
        for (WatchRow w : watches) {
            Set<String> seen = new HashSet<>(jdbc.queryForList("SELECT listing_key FROM market_watch_seen WHERE watch_id=?", String.class, w.id()));
            Set<String> matching = new HashSet<>();
            int alerted = 0;
            for (MarketPage.Listing l : listings) {
                if (!matches(w, l)) continue;
                String key = keys.get(l);
                matching.add(key);
                if (seen.contains(key)) continue;
                jdbc.update("INSERT INTO market_watch_seen(watch_id,listing_key) VALUES(?,?) ON CONFLICT DO NOTHING", w.id(), key);
                if (alerted >= ALERTS_PER_WATCH) continue;
                String name = l.name() == null || l.name().isBlank() ? w.name() : l.name().strip();
                if (name.length() > 200) name = name.substring(0, 200);
                jdbc.update("INSERT INTO market_alert(watch_id,member_id,item_code,item_name,upgrade,quantity,price) VALUES(?,?,?,?,?,?,?)",
                    w.id(), w.memberId(), code, name, l.upgrade(), l.quantity(), l.price());
                alerted++;
            }
            seen.removeAll(matching);   // saiu do market (vendido/retirado): se voltar, avisa de novo
            for (String gone : seen) jdbc.update("DELETE FROM market_watch_seen WHERE watch_id=? AND listing_key=?", w.id(), gone);
            created += alerted;
        }
        return created;
    }

    static boolean matches(WatchRow w, MarketPage.Listing l) {
        if (w.minUpgrade() != null && l.upgrade() < w.minUpgrade()) return false;
        return w.maxPrice() == null || unit(l) <= w.maxPrice();
    }

    static double unit(MarketPage.Listing l) { return (double) l.price() / l.quantity(); }

    /** Número do anúncio no market; sem ele, o conteúdo (repetidos ganham #2, #3...). */
    static Map<MarketPage.Listing, String> keys(List<MarketPage.Listing> listings) {
        Map<MarketPage.Listing, String> out = new IdentityHashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (MarketPage.Listing l : listings) {
            if (l.id() > 0) { out.put(l, "id:" + l.id()); continue; }
            String base = l.upgrade() + "|" + l.quantity() + "|" + l.price();
            int k = count.merge(base, 1, Integer::sum);
            out.put(l, "c:" + base + "#" + k);
        }
        return out;
    }
}
