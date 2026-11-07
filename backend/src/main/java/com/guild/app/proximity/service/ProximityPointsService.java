package com.guild.app.proximity.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.characterstatus.entity.CharacterStatus;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.PointsModality;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.points.service.PointsService;
import com.guild.app.websocket.WebSocketPublisher;
import jakarta.validation.constraints.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pontos por aproximação. O Moderador+ escolhe um ou mais membros de referência e os pontos; a DLL de cada
 * referência (que consulta o site a cada poucos segundos) lê os jogadores em volta dele no jogo e devolve quem
 * está a até {@link #RANGE} de distância. Quando todas as referências respondem (ou depois de {@link #TIMEOUT}),
 * cada membro aprovado da lista ganha os pontos. Quem está perto não precisa da DLL: basta o nick do site ser o
 * nome do personagem.
 * Os pedidos ficam só em memória (duram segundos); sem pedido aberto a consulta da DLL nem toca no banco.
 */
@Slf4j
@Service
public class ProximityPointsService {
    /** Distância no mundo do jogo (X/Z, as mesmas unidades da posição da raid), pedida pela liderança. */
    public static final int RANGE = 900;
    static final Duration TIMEOUT = Duration.ofSeconds(15);
    static final Duration KEEP = Duration.ofMinutes(10);
    static final Duration ONLINE = Duration.ofMinutes(3);
    static final int MAX_ANCHORS = 10;
    static final int MAX_NEARBY = 300;

    private final MemberRepository members;
    private final CharacterStatusRepository statuses;
    private final PointsService pointsService;
    private final AuditLogService audit;
    private final WebSocketPublisher ws;
    private final TransactionTemplate tx;
    private final Map<Long, Request> requests = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    Clock clock = Clock.systemUTC();

    public ProximityPointsService(MemberRepository members, CharacterStatusRepository statuses, PointsService pointsService,
                                  AuditLogService audit, WebSocketPublisher ws, PlatformTransactionManager transactions) {
        this.members = members;
        this.statuses = statuses;
        this.pointsService = pointsService;
        this.audit = audit;
        this.ws = ws;
        this.tx = new TransactionTemplate(transactions);
    }

    public record CreateRequest(
            @NotEmpty @Size(max = MAX_ANCHORS) List<@NotNull Long> anchorIds,
            @NotNull @Min(1) @Max(1000) Integer amount,
            boolean includeAnchors,
            @Size(max = 200) String reason) {}

    /** Membro com a DLL no jogo agora (enviou o local nos últimos minutos): candidato a referência. */
    public record AnchorOption(Long memberId, String nickname, String map) {}

    public record Nearby(String nick, int distance) {}

    public record AnchorState(String nickname, boolean answered, int nearby) {}

    public record Awarded(String nickname, int distance, String near) {}

    /** WAITING: esperando as DLLs; DONE: pontos dados (awarded pode ser vazio); FAILED: erro ao dar os pontos. */
    public record Status(long id, String state, int amount, int range, List<AnchorState> anchors, List<Awarded> awarded,
                         int outsiders, String error) {}

    private static final class Request {
        final long id;
        final long actorId;
        final int amount;
        final String reason;
        final boolean includeAnchors;
        final Instant createdAt;
        final Map<Long, String> anchors;                       // id -> nick, na ordem escolhida
        final Map<Long, List<Nearby>> answers = new ConcurrentHashMap<>();
        volatile Status result;

        Request(long id, long actorId, int amount, String reason, boolean includeAnchors, Instant createdAt,
                Map<Long, String> anchors) {
            this.id = id;
            this.actorId = actorId;
            this.amount = amount;
            this.reason = reason;
            this.includeAnchors = includeAnchors;
            this.createdAt = createdAt;
            this.anchors = anchors;
        }
    }

    @Transactional(readOnly = true)
    public List<AnchorOption> anchors() {
        SecurityUtils.requireRole(Role.MODERADOR);
        var latest = new HashMap<Long, CharacterStatus>();
        for (CharacterStatus s : statuses.findByLocationAtAfter(clock.instant().minus(ONLINE))) {
            if (!eligible(s.getMember())) continue;
            latest.merge(s.getMember().getId(), s, (a, b) -> a.getLocationAt().isAfter(b.getLocationAt()) ? a : b);
        }
        return latest.values().stream()
                .map(s -> new AnchorOption(s.getMember().getId(), s.getMember().getNickname(), s.getMapName()))
                .sorted(Comparator.comparing(AnchorOption::nickname, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public Status create(CreateRequest request, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.MODERADOR);
        var anchors = new LinkedHashMap<Long, String>();
        for (Long id : request.anchorIds()) {
            Member member = members.findById(id).filter(ProximityPointsService::eligible)
                    .orElseThrow(() -> new AppException("Membro de referência não encontrado.", HttpStatus.BAD_REQUEST));
            anchors.put(member.getId(), member.getNickname());
        }
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        long id = ids.incrementAndGet();
        var created = new Request(id, actor.getId(), request.amount(), reason, request.includeAnchors(), clock.instant(), anchors);
        requests.put(id, created);
        return status(created);
    }

    public Status status(long id) {
        SecurityUtils.requireRole(Role.MODERADOR);
        Request request = requests.get(id);
        if (request == null) throw new AppException("Pedido não encontrado.", HttpStatus.NOT_FOUND);
        if (request.result == null && timedOut(request)) finish(request);
        return status(request);
    }

    /** Pedidos abertos em que este personagem é referência e ainda não respondeu: "id&lt;TAB&gt;alcance" por linha. */
    public List<String> forGame(String character) {
        if (requests.isEmpty()) return List.of();
        boolean open = requests.values().stream().anyMatch(r -> r.result == null && !timedOut(r));
        if (!open) return List.of();
        Set<Long> mine = memberIds(character);
        if (mine.isEmpty()) return List.of();
        var lines = new ArrayList<String>();
        for (Request r : requests.values()) {
            if (r.result != null || timedOut(r)) continue;
            for (Long anchor : r.anchors.keySet())
                if (mine.contains(anchor) && !r.answers.containsKey(anchor)) { lines.add(r.id + "\t" + RANGE); break; }
        }
        return lines;
    }

    /** Resposta da DLL de uma referência: "nick&lt;TAB&gt;distância" por linha, só quem está perto. */
    public void report(long id, String character, String body) {
        Request r = requests.get(id);
        if (r == null || r.result != null || timedOut(r))
            throw new AppException("Pedido encerrado.", HttpStatus.GONE);
        Set<Long> mine = memberIds(character);
        Long anchor = r.anchors.keySet().stream().filter(mine::contains).findFirst()
                .orElseThrow(() -> new AppException("Personagem não é referência deste pedido.", HttpStatus.FORBIDDEN));
        r.answers.putIfAbsent(anchor, parse(body));
        if (r.answers.keySet().containsAll(r.anchors.keySet())) finish(r);
    }

    /** Pedidos que passaram do tempo sem ninguém olhar o modal também dão os pontos; os velhos saem da memória. */
    @Scheduled(fixedDelay = 2000)
    public void sweep() {
        Instant now = clock.instant();
        for (Request r : requests.values()) {
            if (r.result == null && timedOut(r)) finish(r);
            else if (r.result != null && now.isAfter(r.createdAt.plus(KEEP))) requests.remove(r.id);
        }
    }

    static List<Nearby> parse(String body) {
        var out = new ArrayList<Nearby>();
        if (body == null) return out;
        var seen = new HashSet<String>();
        for (String line : body.split("\n")) {
            if (out.size() >= MAX_NEARBY) break;
            String[] parts = line.strip().split("\t");
            if (parts.length != 2) continue;
            String nick = parts[0].strip();
            if (nick.isEmpty() || nick.length() > 32 || nick.chars().anyMatch(Character::isISOControl)) continue;
            int distance;
            try { distance = Integer.parseInt(parts[1].strip()); } catch (NumberFormatException e) { continue; }
            if (distance < 0 || distance > RANGE) continue;
            if (seen.add(nick.toLowerCase(Locale.ROOT))) out.add(new Nearby(nick, distance));
        }
        return out;
    }

    private boolean timedOut(Request r) {
        return !clock.instant().isBefore(r.createdAt.plus(TIMEOUT));
    }

    private Set<Long> memberIds(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Set.of();
        var out = new HashSet<Long>();
        for (Member m : members.findByGameCharacter(character.strip())) if (eligible(m)) out.add(m.getId());
        return out;
    }

    private void finish(Request r) {
        synchronized (r) {
            if (r.result != null) return;
            try {
                r.result = tx.execute(t -> award(r));
            } catch (RuntimeException e) {
                log.warn("Pontos por aproximação {} falharam", r.id, e);
                r.result = new Status(r.id, "FAILED", r.amount, RANGE, anchorStates(r), List.of(), 0,
                        "Não foi possível dar os pontos. Tente de novo.");
            }
        }
    }

    private Status award(Request r) {
        // Quem cada referência viu (a menor distância vale), depois as próprias referências que responderam.
        var chosen = new TreeMap<Long, Awarded>();
        int outsiders = 0;
        var outsiderNicks = new HashSet<String>();
        for (var answer : r.answers.entrySet()) {
            String near = r.anchors.get(answer.getKey());
            for (Nearby n : answer.getValue()) {
                Optional<Member> member = members.findByGameCharacter(n.nick()).stream()
                        .filter(ProximityPointsService::eligible).min(Comparator.comparing(Member::getId));
                if (member.isEmpty()) {
                    if (outsiderNicks.add(n.nick().toLowerCase(Locale.ROOT))) outsiders++;
                    continue;
                }
                var awarded = new Awarded(member.get().getNickname(), n.distance(), near);
                chosen.merge(member.get().getId(), awarded, (a, b) -> a.distance() <= b.distance() ? a : b);
            }
        }
        if (r.includeAnchors)
            for (Long anchor : r.answers.keySet()) chosen.put(anchor, new Awarded(r.anchors.get(anchor), 0, r.anchors.get(anchor)));

        String nearList = String.join(", ", r.anchors.values());
        String reason = r.reason == null ? "Pontos por aproximação: perto de " + nearList
                : r.reason + " (perto de " + nearList + ")";
        Member actor = members.getReferenceById(r.actorId);
        // Ordem fixa de ids: dois pedidos juntos travam as linhas na mesma ordem.
        for (Long memberId : chosen.keySet()) {
            Member member = members.findByIdForUpdate(memberId).orElseThrow();
            pointsService.adjustPoints(member, actor, r.amount, PointsModality.AJUSTE, reason);
            audit.log(AuditLogType.POINTS_ADJUSTED, actor, member, Map.of(
                    "amount", r.amount,
                    "modality", PointsModality.AJUSTE.name(),
                    "reason", reason,
                    "source", "PROXIMITY"));
            ws.publishPointsUpdate(memberId, () -> Map.of(
                    "memberId", memberId,
                    "availablePoints", pointsService.getAvailablePoints(memberId)));
        }
        var awarded = chosen.values().stream()
                .sorted(Comparator.comparing(Awarded::nickname, String.CASE_INSENSITIVE_ORDER)).toList();
        return new Status(r.id, "DONE", r.amount, RANGE, anchorStates(r), awarded, outsiders, null);
    }

    private Status status(Request r) {
        Status done = r.result;
        if (done != null) return done;
        return new Status(r.id, "WAITING", r.amount, RANGE, anchorStates(r), List.of(), 0, null);
    }

    private static List<AnchorState> anchorStates(Request r) {
        return r.anchors.entrySet().stream().map(e -> {
            List<Nearby> seen = r.answers.get(e.getKey());
            return new AnchorState(e.getValue(), seen != null, seen == null ? 0 : seen.size());
        }).toList();
    }

    private static boolean eligible(Member member) {
        return member.getStatus() == MemberStatus.APROVADO && member.isProfileComplete()
                && member.getDeletedAt() == null && member.getSuspendedAt() == null;
    }
}
