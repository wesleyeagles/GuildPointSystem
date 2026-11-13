package com.guild.app.auction.service;

import com.guild.app.auction.dto.*;
import com.guild.app.auction.entity.*;
import com.guild.app.auction.repository.*;
import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.AuctionStatus;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.PointsModality;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.emoji.service.GuildEmojiService;
import com.guild.app.item.dto.ItemResponse;
import com.guild.app.item.entity.GameLuaItem;
import com.guild.app.item.repository.GameLuaItemRepository;
import com.guild.app.item.repository.ItemRepository;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.points.service.PointsService;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(1, 5, 15, 30, 60, 180, 360, 720, 1440);
    private static final int CLOSED_LIST_LIMIT = 50;
    /** A start time closer than this just opens the auction now. */
    private static final long SCHEDULE_MIN_LEAD_SECONDS = 30;
    private static final long SCHEDULE_MAX_DAYS = 30;
    static final Set<Integer> EXTEND_MINUTES = Set.of(1, 5, 15, 30, 60);
    /** A resumed auction gets at least this much time, so it never drops straight into DOLE. */
    static final long RESUME_MIN_SECONDS = 30;
    /** Distinct emojis per message, as on Discord. */
    private static final int MAX_REACTION_EMOJIS = 20;

    /** A bid with this much time left (or less) resets the clock to {@link #EXTENDED_SECONDS}. */
    static final long EXTEND_THRESHOLD_MS = 15_000;
    static final long EXTENDED_SECONDS = 25;
    static final long DOLE_STEP_SECONDS = 2;
    static final long DOLE_RESCUE_SECONDS = 10;

    /** Tie-break timeline: announcement countdown → free spin → landing on the drawn winner. */
    static final long TIE_COUNTDOWN_SECONDS = 5;
    static final long TIE_SPIN_SECONDS = 6;
    static final long TIE_LANDING_SECONDS = 5;
    static final short TIE_PHASE_COUNTDOWN = 1;
    static final short TIE_PHASE_SPIN = 2;
    static final short TIE_PHASE_LANDING = 3;

    /** Started and not finished: bids reserve points here (PAUSED keeps them held). */
    static final List<AuctionStatus> ACTIVE_STATUSES =
            List.of(AuctionStatus.OPEN, AuctionStatus.DOLE, AuctionStatus.TIE_BREAK, AuctionStatus.PAUSED);
    /** Statuses whose {@code endsAt} drives the scheduler. */
    static final List<AuctionStatus> TIMED_STATUSES =
            List.of(AuctionStatus.OPEN, AuctionStatus.DOLE, AuctionStatus.TIE_BREAK);
    static final List<AuctionStatus> FINISHED_STATUSES =
            List.of(AuctionStatus.CLOSED, AuctionStatus.CANCELLED);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuctionRepository auctionRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final AuctionMessageRepository auctionMessageRepository;
    private final AuctionMessageReactionRepository auctionMessageReactionRepository;
    private final ItemRepository itemRepository;
    private final GameLuaItemRepository gameLuaItemRepository;
    private final MemberRepository memberRepository;
    private final PointsService pointsService;
    private final AuditLogService auditLogService;
    private final WebSocketPublisher webSocketPublisher;
    private final GuildEmojiService guildEmojiService;
    private final ReactionRateLimiter reactionRateLimiter;

    /**
     * Items of active auctions: they never change after creation, and the response is rebuilt on every
     * bid and timer tick (inside the bid's row lock). Dropped once the auction leaves the active statuses.
     */
    private final Map<Long, List<AuctionResponse.AuctionItemResponse>> activeItems = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<AuctionResponse> listOpen() {
        var active = auctionRepository.findByStatusInOrderByCreatedAtDesc(ACTIVE_STATUSES);
        var closed = auctionRepository.findByStatusInOrderByCreatedAtDesc(FINISHED_STATUSES).stream()
                .limit(CLOSED_LIST_LIMIT)
                .toList();

        // Timed ones first by start time, then the "em breve" ones (no time).
        var scheduled = auctionRepository.findByStatusOrderByCreatedAtDesc(AuctionStatus.SCHEDULED).stream()
                .sorted(Comparator.comparing(Auction::getStartsAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        var combined = new ArrayList<Auction>(active.size() + scheduled.size() + closed.size());
        combined.addAll(active);
        combined.addAll(scheduled);
        combined.addAll(closed);
        return combined.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AuctionResponse getById(Long id) {
        var auction = auctionRepository.findByIdWithWinner(id)
                .orElseThrow(() -> new AppException("Leilão não encontrado.", HttpStatus.NOT_FOUND));
        return toResponse(auction);
    }

    @Transactional
    public AuctionResponse create(CreateAuctionRequest request, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        if (!ALLOWED_DURATIONS.contains(request.durationMinutes())) {
            throw new AppException("Duração inválida.", HttpStatus.BAD_REQUEST);
        }

        var now = Instant.now();
        var startsAt = request.startsAt();
        var endsAt = request.endsAt();
        if (endsAt != null && (startsAt != null || Boolean.TRUE.equals(request.startLater())
                || !endsAt.isAfter(now) || endsAt.isAfter(now.plus(Duration.ofDays(1))))) {
            throw new AppException("O encerramento deve estar nas próximas 24 horas, sem agendamento.",
                    HttpStatus.BAD_REQUEST);
        }
        boolean timed = startsAt != null && startsAt.isAfter(now.plusSeconds(SCHEDULE_MIN_LEAD_SECONDS));
        boolean scheduled = timed || (startsAt == null && Boolean.TRUE.equals(request.startLater()));
        if (timed && startsAt.isAfter(now.plus(Duration.ofDays(SCHEDULE_MAX_DAYS)))) {
            throw new AppException("O início pode ser no máximo " + SCHEDULE_MAX_DAYS + " dias à frente.",
                    HttpStatus.BAD_REQUEST);
        }

        var auction = new Auction();
        auction.setDurationMinutes(request.durationMinutes());
        auction.setCreatedBy(memberRepository.getReferenceById(actor.getId()));
        if (scheduled) {
            auction.setStatus(AuctionStatus.SCHEDULED);
            auction.setStartsAt(timed ? startsAt : null);
            // Placeholder until it opens (the column is NOT NULL); open() sets the real end.
            auction.setEndsAt((timed ? startsAt : now).plusSeconds(request.durationMinutes() * 60L));
        } else {
            auction.setStartsAt(now);
            auction.setEndsAt(endsAt != null ? endsAt : now.plusSeconds(request.durationMinutes() * 60L));
        }
        auction = auctionRepository.save(auction);

        for (var entry : request.items()) {
            var item = itemRepository.findById(entry.itemId())
                    .orElseThrow(() -> new AppException("Item não encontrado.", HttpStatus.NOT_FOUND));
            var ai = new AuctionItem();
            ai.setAuction(auction);
            ai.setItem(item);
            ai.setQuantity(entry.quantity());
            auctionItemRepository.save(ai);
        }

        var details = new HashMap<String, Object>();
        details.put("auctionId", auction.getId());
        details.put("durationMinutes", request.durationMinutes());
        if (scheduled) details.put("startsAt", timed ? startsAt.toString() : "manual");
        auditLogService.log(AuditLogType.AUCTION_CREATED,
                memberRepository.getReferenceById(actor.getId()), null, details);

        var response = toResponse(auction);
        if (scheduled) {
            // No announcement yet: members get the "new auction" toast when it opens.
            webSocketPublisher.publishAuctionList(Map.of("type", "AUCTION_SCHEDULED", "auctionId", auction.getId()));
        } else {
            // Global announcement: every connected client listens on /topic/auctions.
            webSocketPublisher.publishAuctionList(Map.of("type", "AUCTION_CREATED", "auction", response));
        }
        publishAuctionUpdate(response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<Long> findDueScheduledIds() {
        return auctionRepository.findDueScheduledIds(Instant.now());
    }

    /** Scheduler: opens a scheduled auction once its start time has passed. */
    @Transactional
    public void startScheduled(Long auctionId) {
        var auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.SCHEDULED
                || auction.getStartsAt() == null || auction.getStartsAt().isAfter(Instant.now())) return;
        open(auction, "Leilão iniciado.");
    }

    /** Líder/Administrador: opens a scheduled or "em breve" auction right now. */
    @Transactional
    public AuctionResponse startNow(Long auctionId, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var auction = lockForAdmin(auctionId);
        if (auction.getStatus() != AuctionStatus.SCHEDULED) {
            throw new AppException("Este leilão já foi iniciado.", HttpStatus.CONFLICT);
        }
        return open(auction, actorName(actor) + " iniciou o leilão.");
    }

    /** SCHEDULED → OPEN: the full duration counts from now, and every member gets the "new auction" toast. */
    private AuctionResponse open(Auction auction, String botText) {
        var now = Instant.now();
        auction.setStatus(AuctionStatus.OPEN);
        auction.setStartsAt(now);
        auction.setEndsAt(now.plusSeconds(auction.getDurationMinutes() * 60L));
        auction.bumpVersion();
        auctionRepository.save(auction);

        var response = toResponse(auction);
        webSocketPublisher.publishAuctionList(Map.of("type", "AUCTION_CREATED", "auction", response));
        publishAuctionUpdate(response);
        addBotMessage(auction, botText);
        return response;
    }

    /**
     * Freezes a running auction: the time left is kept and bids are refused until it is resumed. A DOLE
     * countdown is dropped (resume goes back to OPEN). Reserves stay held: PAUSED is an active status.
     */
    @Transactional
    public AuctionResponse pause(Long auctionId, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var auction = lockForAdmin(auctionId);
        if (auction.getStatus() != AuctionStatus.OPEN && auction.getStatus() != AuctionStatus.DOLE) {
            throw new AppException("Só é possível pausar leilões abertos.", HttpStatus.CONFLICT);
        }
        long left = Math.max(0, auction.getEndsAt().toEpochMilli() - Instant.now().toEpochMilli());
        auction.setPausedRemainingMs(left);
        auction.setStatus(AuctionStatus.PAUSED);
        auction.setDolePhase(0);
        auction.bumpVersion();
        auctionRepository.save(auction);

        var response = toResponse(auction);
        publishAuctionUpdate(response);
        addBotMessage(auction, actorName(actor) + " pausou o leilão. Os lances voltam quando ele for retomado.");
        return response;
    }

    /** PAUSED → OPEN with the time that was left (at least {@link #RESUME_MIN_SECONDS}). */
    @Transactional
    public AuctionResponse resume(Long auctionId, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var auction = lockForAdmin(auctionId);
        if (auction.getStatus() != AuctionStatus.PAUSED) {
            throw new AppException("Este leilão não está pausado.", HttpStatus.CONFLICT);
        }
        long left = Math.max(RESUME_MIN_SECONDS * 1000,
                auction.getPausedRemainingMs() != null ? auction.getPausedRemainingMs() : 0);
        auction.setStatus(AuctionStatus.OPEN);
        auction.setEndsAt(Instant.now().plusMillis(left));
        auction.setPausedRemainingMs(null);
        auction.bumpVersion();
        auctionRepository.save(auction);

        var response = toResponse(auction);
        publishAuctionUpdate(response);
        addBotMessage(auction, actorName(actor) + " retomou o leilão.");
        return response;
    }

    private Auction lockForAdmin(Long auctionId) {
        return auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AppException("Leilão não encontrado.", HttpStatus.NOT_FOUND));
    }

    private String actorName(MemberPrincipal actor) {
        return memberRepository.findById(actor.getId()).map(Member::getNickname).orElse("A administração");
    }

    /**
     * Bids on one auction are serialized by the row lock, so among concurrent bids the first to
     * acquire it wins and every later one is validated against the already-updated current bid.
     */
    @Transactional
    public AuctionResponse placeBid(Long auctionId, long amount, Long actorId) {
        if (amount < 1) {
            throw new AppException("O lance deve ser de no mínimo 1 ponto.", HttpStatus.BAD_REQUEST);
        }
        var auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AppException("Leilão não encontrado.", HttpStatus.NOT_FOUND));

        var now = Instant.now();
        if (auction.getStatus() == AuctionStatus.SCHEDULED) {
            throw new AppException("Este leilão ainda não começou.", HttpStatus.CONFLICT);
        }
        if (auction.getStatus() == AuctionStatus.PAUSED) {
            throw new AppException("Leilão pausado pela administração: aguarde ele ser retomado.", HttpStatus.CONFLICT);
        }
        if (auction.getStatus() != AuctionStatus.OPEN && auction.getStatus() != AuctionStatus.DOLE) {
            throw new AppException("Este leilão não está mais aceitando lances.", HttpStatus.CONFLICT);
        }
        if (auction.getStatus() == AuctionStatus.OPEN && !auction.getEndsAt().isAfter(now)) {
            throw new AppException("Tempo esgotado: o lance chegou depois do fim do leilão.", HttpStatus.CONFLICT);
        }

        // Member lock: prevents the same points from being committed in two auctions at once.
        var member = memberRepository.findByIdForUpdate(actorId)
                .orElseThrow(() -> new AppException("Membro não encontrado.", HttpStatus.NOT_FOUND));
        if (member.getStatus() != MemberStatus.APROVADO) {
            throw new AppException("Apenas membros aprovados podem dar lances.", HttpStatus.FORBIDDEN);
        }

        long currentBid = auction.getCurrentBid();
        var previousLeaders = currentBid > 0
                ? auctionBidRepository.findTiedMemberIds(auctionId, currentBid)
                : List.<Long>of();
        if (previousLeaders.contains(actorId)) {
            throw new AppException("Você já está com o maior lance (" + currentBid + ").", HttpStatus.CONFLICT);
        }

        long available = pointsService.getAvailablePoints(member, auctionId);
        if (amount > available) {
            throw new AppException("Saldo disponível insuficiente: você tem " + available + " pts.",
                    HttpStatus.BAD_REQUEST);
        }
        if (amount < currentBid) {
            throw new AppException("Lance recusado: o lance atual já é " + currentBid
                    + " (" + leaderLabel(previousLeaders) + "), maior que o seu " + amount + ".",
                    HttpStatus.CONFLICT);
        }
        if (amount == currentBid && amount != available) {
            throw new AppException("Lance recusado: o lance atual já é " + currentBid
                    + " (" + leaderLabel(previousLeaders) + "). Empatar só é permitido com all-in"
                    + " (todo o seu saldo disponível: " + available + " pts).",
                    HttpStatus.CONFLICT);
        }

        var bid = new AuctionBid();
        bid.setAuction(auction);
        bid.setMember(member);
        bid.setAmount(amount);
        auctionBidRepository.save(bid);

        auction.setCurrentBid(amount);
        if (auction.getStatus() == AuctionStatus.DOLE) {
            // Bid rescued the auction from DOLE — back to OPEN with +10s.
            auction.setEndsAt(now.plusSeconds(DOLE_RESCUE_SECONDS));
            auction.setStatus(AuctionStatus.OPEN);
            auction.setDolePhase(0);
        } else if (auction.getEndsAt().toEpochMilli() - now.toEpochMilli() <= EXTEND_THRESHOLD_MS) {
            auction.setEndsAt(now.plusSeconds(EXTENDED_SECONDS));
        }
        auction.bumpVersion();
        auctionRepository.save(auction);

        boolean tie = amount == currentBid;
        addBidMessage(auction, member, amount, tie);
        // Holders of the new top bid, in the order they reached it: no need to query them again.
        var leaders = new ArrayList<Long>(tie ? previousLeaders : List.of());
        leaders.add(actorId);
        var response = toResponse(auction, leaders);
        publishAuctionUpdate(response);
        publishPointsUpdate(actorId);
        // Outbid leaders get their reserve released (a tie keeps it reserved, but refresh anyway).
        previousLeaders.forEach(this::publishPointsUpdate);
        return response;
    }

    @Transactional
    public AuctionResponse placeBid(Long auctionId, PlaceBidRequest request, MemberPrincipal actor) {
        return placeBid(auctionId, request.amount(), actor.getId());
    }

    /**
     * Force-closes an active auction with no winner. Nobody is charged: points were only reserved
     * (available balance excludes active auctions), so moving out of an active status releases them.
     */
    @Transactional
    public AuctionResponse cancel(Long auctionId, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AppException("Leilão não encontrado.", HttpStatus.NOT_FOUND));
        if (!ACTIVE_STATUSES.contains(auction.getStatus())) {
            throw new AppException("Só é possível encerrar leilões em andamento.", HttpStatus.CONFLICT);
        }

        var reservedIds = auction.getCurrentBid() > 0
                ? auctionBidRepository.findTiedMemberIds(auctionId, auction.getCurrentBid())
                : List.<Long>of();
        var actorMember = memberRepository.getReferenceById(actor.getId());

        auction.setStatus(AuctionStatus.CANCELLED);
        auction.setWinner(null);
        auction.setDolePhase(0);
        auction.setTieBreakPhase((short) 0);
        auction.bumpVersion();
        auctionRepository.save(auction);

        auditLogService.log(AuditLogType.AUCTION_CANCELLED, actorMember, null, Map.of(
                "auctionId", auctionId,
                "amount", auction.getCurrentBid(),
                "releasedMemberIds", reservedIds));

        var response = toResponse(auction);
        var payload = new HashMap<String, Object>();
        payload.put("type", "AUCTION_CANCELLED");
        payload.put("auctionId", auctionId);
        payload.put("version", auction.getStateVersion());
        webSocketPublisher.publishAuction(auctionId, payload);
        publishAuctionUpdate(response);
        addBotMessage(auction, "Leilão encerrado pela administração. Ninguém foi cobrado"
                + (reservedIds.isEmpty() ? "." : " e os pontos reservados foram devolvidos."));
        // Released reserves show up in the members' available balance immediately.
        reservedIds.forEach(this::publishPointsUpdate);
        return response;
    }

    /**
     * Adds {@code minutes} (one of {@link #EXTEND_MINUTES}) to a running or paused auction. During DOLE the
     * remaining time is only the countdown step, so the auction goes back to OPEN with the extension from now.
     */
    @Transactional
    public AuctionResponse extend(Long auctionId, int minutes, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        if (!EXTEND_MINUTES.contains(minutes)) {
            throw new AppException("Tempo inválido.", HttpStatus.BAD_REQUEST);
        }
        long extendSeconds = minutes * 60L;
        var auction = lockForAdmin(auctionId);
        var status = auction.getStatus();
        if (status != AuctionStatus.OPEN && status != AuctionStatus.DOLE && status != AuctionStatus.PAUSED) {
            throw new AppException("Só é possível adicionar tempo a leilões abertos ou pausados.", HttpStatus.CONFLICT);
        }

        var now = Instant.now();
        if (status == AuctionStatus.PAUSED) {
            long left = auction.getPausedRemainingMs() != null ? auction.getPausedRemainingMs() : 0;
            auction.setPausedRemainingMs(left + extendSeconds * 1000);
        } else if (status == AuctionStatus.DOLE) {
            auction.setStatus(AuctionStatus.OPEN);
            auction.setDolePhase(0);
            auction.setEndsAt(now.plusSeconds(extendSeconds));
        } else {
            var base = auction.getEndsAt().isAfter(now) ? auction.getEndsAt() : now;
            auction.setEndsAt(base.plusSeconds(extendSeconds));
        }
        auction.bumpVersion();
        auctionRepository.save(auction);

        var response = toResponse(auction);
        publishAuctionUpdate(response);
        addBotMessage(auction, actorName(actor) + " adicionou "
                + (minutes == 60 ? "1 hora" : minutes == 1 ? "1 minuto" : minutes + " minutos") + " ao leilão.");
        return response;
    }

    /** Deletes a finished auction with its bids and chat. Points are untouched (debits stay in history). */
    @Transactional
    public void delete(Long auctionId, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AppException("Leilão não encontrado.", HttpStatus.NOT_FOUND));
        // A scheduled auction has no bids yet, so it can go without being cancelled first.
        if (!FINISHED_STATUSES.contains(auction.getStatus()) && auction.getStatus() != AuctionStatus.SCHEDULED) {
            throw new AppException("Encerre o leilão antes de excluí-lo.", HttpStatus.CONFLICT);
        }

        var itemNames = auctionItemRepository.findByAuctionIdWithItem(auctionId).stream()
                .map(ai -> ai.getItem().getName())
                .collect(Collectors.joining(", "));
        var payload = new HashMap<String, Object>();
        payload.put("auctionId", auctionId);
        payload.put("status", auction.getStatus().name());
        payload.put("items", itemNames);
        payload.put("amount", auction.getCurrentBid());
        payload.put("winnerId", auction.getWinner() != null ? auction.getWinner().getId() : "none");
        auditLogService.log(AuditLogType.AUCTION_DELETED, memberRepository.getReferenceById(actor.getId()), null, payload);

        // Bulk delete in the DB (cascades to bids, items, messages) — loaded children never block it.
        auctionRepository.deleteByIdCascade(auctionId);

        var event = Map.of("type", "AUCTION_DELETED", "auctionId", auctionId);
        webSocketPublisher.publishAuction(auctionId, event);
        webSocketPublisher.publishAuctionList(event);
    }

    @Transactional
    public void sendChatMessage(Long auctionId, ChatMessageRequest request, MemberPrincipal actor) {
        var auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AppException("Leilão não encontrado.", HttpStatus.NOT_FOUND));
        if (FINISHED_STATUSES.contains(auction.getStatus())) {
            throw new AppException("Este leilão já foi encerrado.", HttpStatus.BAD_REQUEST);
        }
        var msg = new AuctionMessage();
        msg.setAuction(auction);
        msg.setMember(memberRepository.getReferenceById(actor.getId()));
        msg.setType("TEXT");
        msg.setContent(request.content());
        if (request.replyToId() != null) {
            var replyTo = auctionMessageRepository.findById(request.replyToId())
                    .filter(m -> m.getAuction().getId().equals(auctionId))
                    .orElseThrow(() -> new AppException("A mensagem respondida não existe mais.",
                            HttpStatus.BAD_REQUEST));
            if (!"TEXT".equals(replyTo.getType())) {
                throw new AppException("Mensagens do sistema não podem ser respondidas.", HttpStatus.BAD_REQUEST);
            }
            msg.setReplyTo(replyTo);
        }
        msg = auctionMessageRepository.save(msg);
        webSocketPublisher.publishAuction(auctionId, Map.of(
                "type", "CHAT", "message", toMessageResponse(msg)));
    }

    @Transactional(readOnly = true)
    public List<AuctionMessageResponse> getMessages(Long auctionId) {
        var reactions = auctionMessageReactionRepository.findByAuctionId(auctionId).stream()
                .collect(Collectors.groupingBy(r -> r.getMessage().getId()));
        return auctionMessageRepository.findByAuctionIdOrderByCreatedAtAsc(auctionId).stream()
                .map(msg -> toMessageResponse(msg, reactions.getOrDefault(msg.getId(), List.of())))
                .toList();
    }

    /** Adds the member's reaction with this emoji, or removes it when it is already there. */
    @Transactional
    public void toggleReaction(Long auctionId, Long messageId, String value, MemberPrincipal actor) {
        if (!reactionRateLimiter.tryAcquire(actor.getId())) {
            throw new AppException("Calma! Muitas reações em sequência.", HttpStatus.TOO_MANY_REQUESTS);
        }
        // Unicode emoji, or a guild emoji as ":name:" (stored with the registered name's case).
        String emoji = isEmoji(value) ? value : guildEmojiService.canonicalReactionKey(value)
                .orElseThrow(() -> new AppException("Emoji inválido.", HttpStatus.BAD_REQUEST));
        var message = auctionMessageRepository.findById(messageId)
                .filter(m -> m.getAuction().getId().equals(auctionId))
                .orElseThrow(() -> new AppException("Mensagem não encontrada.", HttpStatus.NOT_FOUND));
        if (FINISHED_STATUSES.contains(message.getAuction().getStatus())) {
            throw new AppException("Este leilão já foi encerrado.", HttpStatus.BAD_REQUEST);
        }

        var existing = auctionMessageReactionRepository
                .findByMessageIdAndMemberIdAndEmoji(messageId, actor.getId(), emoji);
        if (existing.isPresent()) {
            auctionMessageReactionRepository.delete(existing.get());
        } else {
            boolean newEmoji = !auctionMessageReactionRepository.existsByMessageIdAndEmoji(messageId, emoji);
            if (newEmoji && auctionMessageReactionRepository.countDistinctEmojis(messageId) >= MAX_REACTION_EMOJIS) {
                throw new AppException("Esta mensagem já tem o máximo de " + MAX_REACTION_EMOJIS + " reações diferentes.",
                        HttpStatus.BAD_REQUEST);
            }
            var reaction = new AuctionMessageReaction();
            reaction.setMessage(message);
            reaction.setMember(memberRepository.getReferenceById(actor.getId()));
            reaction.setEmoji(emoji);
            auctionMessageReactionRepository.save(reaction);
        }
        auctionMessageReactionRepository.flush();

        webSocketPublisher.publishAuction(auctionId, Map.of(
                "type", "REACTION",
                "messageId", messageId,
                "reactions", toReactionSummaries(auctionMessageReactionRepository.findByMessageId(messageId))));
    }

    /**
     * A Unicode emoji (sequence): pictographs plus the joiners, variation selectors, skin tones, keycaps
     * and tag characters they are built from — no letters, spaces or markup.
     */
    static boolean isEmoji(String value) {
        if (value == null || value.isBlank()) return false;
        boolean hasPictograph = false;
        for (int cp : value.codePoints().toArray()) {
            int type = Character.getType(cp);
            if (type == Character.OTHER_SYMBOL) {
                hasPictograph = true;
            } else if (type != Character.MODIFIER_SYMBOL
                    && type != Character.NON_SPACING_MARK
                    && type != Character.ENCLOSING_MARK
                    && type != Character.FORMAT
                    && type != Character.DECIMAL_DIGIT_NUMBER
                    && cp != '#' && cp != '*') {
                return false;
            }
        }
        return hasPictograph;
    }

    /** Active auctions where the member currently holds (or ties) the top bid. */
    @Transactional(readOnly = true)
    public List<Long> findActiveAuctionsLedBy(Long memberId) {
        return auctionRepository.findIdsLedBy(memberId, ACTIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public List<Long> findDueAuctionIds() {
        return auctionRepository.findDueIds(TIMED_STATUSES, Instant.now());
    }

    /** Advances one auction whose timer expired. Each call runs in its own transaction. */
    @Transactional
    public void advance(Long auctionId) {
        var auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        // Guards: a bid may have extended the timer, or another tick already advanced it.
        if (auction == null || auction.getEndsAt().isAfter(Instant.now())) return;

        switch (auction.getStatus()) {
            case OPEN -> startDole(auction);
            case DOLE -> advanceDole(auction);
            case TIE_BREAK -> advanceTieBreak(auction);
            default -> { }
        }
    }

    private void startDole(Auction auction) {
        if (auction.getCurrentBid() == 0) {
            closeWithWinner(auction, null);
            return;
        }
        auction.setStatus(AuctionStatus.DOLE);
        auction.setDolePhase(1);
        auction.setEndsAt(Instant.now().plusSeconds(DOLE_STEP_SECONDS));
        auction.bumpVersion();
        auctionRepository.save(auction);
        addBotMessage(auction, "DOLE 1");
        publishAuctionUpdate(toResponse(auction));
    }

    private void advanceDole(Auction auction) {
        int phase = auction.getDolePhase();
        if (phase >= 3) {
            resolveAuction(auction);
            return;
        }
        phase++;
        auction.setDolePhase(phase);
        auction.setEndsAt(Instant.now().plusSeconds(DOLE_STEP_SECONDS));
        auction.bumpVersion();
        auctionRepository.save(auction);
        addBotMessage(auction, "DOLE " + phase);
        publishAuctionUpdate(toResponse(auction));
    }

    private void resolveAuction(Auction auction) {
        var tiedIds = auctionBidRepository.findTiedMemberIds(auction.getId(), auction.getCurrentBid());
        if (tiedIds.size() <= 1) {
            closeWithWinner(auction, tiedIds.isEmpty() ? null : tiedIds.getFirst());
            return;
        }

        auction.setStatus(AuctionStatus.TIE_BREAK);
        auction.setDolePhase(0);
        auction.setTieBreakPhase(TIE_PHASE_COUNTDOWN);
        auction.setEndsAt(Instant.now().plusSeconds(TIE_COUNTDOWN_SECONDS));
        auction.bumpVersion();
        auctionRepository.save(auction);

        var tiedMembers = tiedMembers(tiedIds);
        var names = tiedMembers.stream().map(m -> (String) m.get("nickname")).collect(Collectors.joining(", "));
        addBotMessage(auction, "EMPATE em " + auction.getCurrentBid() + " pts entre " + names
                + ". O desempate será feito por roleta aleatória em " + TIE_COUNTDOWN_SECONDS + " segundos.");

        var response = toResponse(auction);
        webSocketPublisher.publishAuction(auction.getId(), Map.of(
                "type", "TIE_BREAK_START",
                "tiedMembers", tiedMembers,
                "countdownEndsAt", auction.getEndsAt(),
                "version", auction.getStateVersion()));
        publishAuctionUpdate(response);
    }

    private void advanceTieBreak(Auction auction) {
        switch (auction.getTieBreakPhase()) {
            case TIE_PHASE_COUNTDOWN -> {
                auction.setTieBreakPhase(TIE_PHASE_SPIN);
                auction.setEndsAt(Instant.now().plusSeconds(TIE_SPIN_SECONDS));
                auction.bumpVersion();
                auctionRepository.save(auction);
                webSocketPublisher.publishAuction(auction.getId(), Map.of(
                        "type", "TIE_BREAK_SPIN",
                        "version", auction.getStateVersion()));
                publishAuctionUpdate(toResponse(auction));
            }
            case TIE_PHASE_SPIN -> {
                // The winner is drawn only now, after the wheel has been spinning blind for a while.
                var tiedIds = auctionBidRepository.findTiedMemberIds(auction.getId(), auction.getCurrentBid());
                if (tiedIds.isEmpty()) {
                    closeWithWinner(auction, null);
                    return;
                }
                int winnerIndex = RANDOM.nextInt(tiedIds.size());
                auction.setWinner(memberRepository.getReferenceById(tiedIds.get(winnerIndex)));
                auction.setTieBreakPhase(TIE_PHASE_LANDING);
                auction.setEndsAt(Instant.now().plusSeconds(TIE_LANDING_SECONDS));
                auction.bumpVersion();
                auctionRepository.save(auction);
                // Only the wheel position is sent; the name is revealed by AUCTION_CLOSED.
                webSocketPublisher.publishAuction(auction.getId(), Map.of(
                        "type", "TIE_BREAK_RESULT",
                        "winnerIndex", winnerIndex,
                        "segmentCount", tiedIds.size(),
                        "landingMs", TIE_LANDING_SECONDS * 1000,
                        "version", auction.getStateVersion()));
                publishAuctionUpdate(toResponse(auction));
            }
            default -> closeWithWinner(auction,
                    auction.getWinner() != null ? auction.getWinner().getId() : null);
        }
    }

    private void closeWithWinner(Auction auction, Long winnerId) {
        auction.setStatus(AuctionStatus.CLOSED);
        auction.setTieBreakPhase((short) 0);
        auction.setDolePhase(0);
        auction.setWinner(winnerId != null ? memberRepository.getReferenceById(winnerId) : null);
        auction.bumpVersion();
        auctionRepository.save(auction);

        long charged = 0;
        String winnerNickname = null;
        if (winnerId != null) {
            var winner = memberRepository.findByIdForUpdate(winnerId).orElseThrow();
            winnerNickname = winner.getNickname();
            // Reserve rules make this the full bid; cap it so an admin correction can't wedge the scheduler.
            charged = Math.min(auction.getCurrentBid(), Math.max(0, winner.getPoints()));
            if (charged > 0) {
                pointsService.adjustPoints(winner, winner, -charged,
                        PointsModality.LEILAO, "Auction win #" + auction.getId());
            }
            publishPointsUpdate(winnerId);
        }

        auditLogService.log(AuditLogType.AUCTION_CLOSED, auction.getCreatedBy(), null, Map.of(
                "auctionId", auction.getId(),
                "winnerId", winnerId != null ? winnerId : "none",
                "amount", auction.getCurrentBid(),
                "charged", charged));

        var payload = new HashMap<String, Object>();
        payload.put("type", "AUCTION_CLOSED");
        payload.put("winnerId", winnerId);
        payload.put("winnerNickname", winnerNickname);
        payload.put("amount", auction.getCurrentBid());
        payload.put("version", auction.getStateVersion());
        webSocketPublisher.publishAuction(auction.getId(), payload);
        publishAuctionUpdate(toResponse(auction));
        // Chat announcement goes out after AUCTION_CLOSED so it never leads the roulette reveal.
        addBotMessage(auction, winnerNickname != null
                ? "Leilão encerrado! Vencedor: " + winnerNickname + " com " + auction.getCurrentBid() + " pts."
                : "Leilão encerrado sem lances.");
    }

    private void addBidMessage(Auction auction, Member member, long amount, boolean tie) {
        var msg = new AuctionMessage();
        msg.setAuction(auction);
        msg.setMember(member);
        msg.setType("BID");
        msg.setContent(member.getNickname() + (tie ? " EMPATOU (all-in) com " : " deu um lance de ") + amount + " pts");
        msg = auctionMessageRepository.save(msg);
        webSocketPublisher.publishAuction(auction.getId(),
                Map.of("type", "BID", "message", toMessageResponse(msg)));
    }

    private void addBotMessage(Auction auction, String text) {
        var msg = new AuctionMessage();
        msg.setAuction(auction);
        msg.setType("BOT");
        msg.setContent(text);
        msg = auctionMessageRepository.save(msg);
        webSocketPublisher.publishAuction(auction.getId(),
                Map.of("type", "BOT", "message", toMessageResponse(msg)));
    }

    private String leaderLabel(List<Long> leaderIds) {
        if (leaderIds.isEmpty()) return "sem líder";
        var names = memberRepository.findAllById(leaderIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));
        return leaderIds.stream().map(id -> names.getOrDefault(id, "?")).collect(Collectors.joining(", "));
    }

    private List<Map<String, Object>> tiedMembers(List<Long> tiedIds) {
        var names = memberRepository.findAllById(tiedIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));
        return tiedIds.stream()
                .map(id -> Map.<String, Object>of("id", id, "nickname", names.getOrDefault(id, "?")))
                .toList();
    }

    private void publishAuctionUpdate(AuctionResponse response) {
        // Sent on every bid and timer tick to every viewer: leave out the items (and their icons).
        var payload = Map.of("type", "AUCTION_UPDATE", "auction", response.withoutItems());
        webSocketPublisher.publishAuction(response.id(), payload);
        webSocketPublisher.publishAuctionList(payload);
    }

    private void publishPointsUpdate(Long memberId) {
        webSocketPublisher.publishPointsUpdate(memberId, () -> Map.of(
                "memberId", memberId,
                "availablePoints", pointsService.getAvailablePoints(memberId)));
    }

    private AuctionResponse toResponse(Auction auction) {
        var tiedIds = auction.getCurrentBid() > 0
                ? auctionBidRepository.findTiedMemberIds(auction.getId(), auction.getCurrentBid())
                : List.<Long>of();
        return toResponse(auction, tiedIds);
    }

    private List<AuctionResponse.AuctionItemResponse> itemsOf(Auction auction) {
        if (!ACTIVE_STATUSES.contains(auction.getStatus())) {
            activeItems.remove(auction.getId());
            return loadItems(auction.getId());
        }
        var cached = activeItems.get(auction.getId());
        if (cached != null) return cached;
        var items = loadItems(auction.getId());
        activeItems.put(auction.getId(), items);
        return items;
    }

    private List<AuctionResponse.AuctionItemResponse> loadItems(Long auctionId) {
        var auctionItems = auctionItemRepository.findByAuctionIdWithItem(auctionId);
        var gameCodes = auctionItems.stream()
                .map(ai -> ai.getItem().getGameCode())
                .filter(code -> code != null && !code.isBlank())
                .toList();
        var luaByCode = gameCodes.isEmpty()
                ? Map.<String, GameLuaItem>of()
                : gameLuaItemRepository.findByGameCodeIn(gameCodes).stream()
                        .collect(Collectors.toMap(GameLuaItem::getGameCode, l -> l, (a, b) -> a));
        return auctionItems.stream()
                .map(ai -> {
                    var lua = ai.getItem().getGameCode() != null ? luaByCode.get(ai.getItem().getGameCode()) : null;
                    return new AuctionResponse.AuctionItemResponse(
                            ai.getItem().getId(),
                            ai.getItem().getName(),
                            ai.getItem().getImageUrl(),
                            ai.getQuantity(),
                            lua == null ? null : new ItemResponse.GameIcon(
                                    lua.getCategory(), lua.getGameIndex(), lua.getGameCode(),
                                    lua.getIconId(), lua.getSpriteSheet(), lua.getSpriteCols()));
                })
                .toList();
    }

    /** {@code tiedIds}: holders of the current top bid, in the order they reached it. */
    private AuctionResponse toResponse(Auction auction, List<Long> tiedIds) {
        var items = itemsOf(auction);
        boolean tied = tiedIds.size() > 1;

        List<String> tiedMemberNicknames = List.of();
        Long leaderId = null;
        String leaderNickname = null;
        if (!tiedIds.isEmpty()) {
            var nicknames = tiedMembers(tiedIds).stream().map(m -> (String) m.get("nickname")).toList();
            if (tied) {
                tiedMemberNicknames = nicknames;
            } else {
                leaderId = tiedIds.getFirst();
                leaderNickname = nicknames.getFirst();
            }
        }

        boolean closed = auction.getStatus() == AuctionStatus.CLOSED;
        var winner = closed ? auction.getWinner() : null;

        return new AuctionResponse(
                auction.getId(),
                auction.getStatus(),
                auction.getDurationMinutes(),
                auction.getCreatedAt(),
                auction.getStartsAt(),
                auction.getEndsAt(),
                auction.getCurrentBid(),
                winner != null ? winner.getId() : null,
                winner != null ? winner.getNickname() : null,
                leaderId,
                leaderNickname,
                tiedIds.size(),
                tied,
                tiedIds,
                tiedMemberNicknames,
                items,
                auction.getDolePhase(),
                auction.getTieBreakPhase(),
                auction.getStateVersion(),
                Instant.now(),
                auction.getStatus() == AuctionStatus.PAUSED ? auction.getPausedRemainingMs() : null);
    }

    private AuctionMessageResponse toMessageResponse(AuctionMessage msg) {
        return toMessageResponse(msg, List.of());
    }

    private AuctionMessageResponse toMessageResponse(AuctionMessage msg, List<AuctionMessageReaction> reactions) {
        var replyTo = msg.getReplyTo();
        return new AuctionMessageResponse(
                msg.getId(),
                msg.getType(),
                msg.getContent(),
                msg.getImageUrl(),
                msg.getCreatedAt(),
                toMemberSummary(msg.getMember()),
                replyTo != null
                        ? new AuctionMessageResponse.ReplySummary(replyTo.getId(), replyTo.getType(),
                                replyTo.getContent(), toMemberSummary(replyTo.getMember()))
                        : null,
                toReactionSummaries(reactions));
    }

    /** Reactions in creation order → one entry per emoji (first-use order) with its members. */
    private static List<AuctionMessageResponse.ReactionSummary> toReactionSummaries(
            List<AuctionMessageReaction> reactions) {
        var byEmoji = new LinkedHashMap<String, List<AuctionMessageResponse.MemberSummary>>();
        for (var r : reactions) {
            byEmoji.computeIfAbsent(r.getEmoji(), e -> new ArrayList<>()).add(toMemberSummary(r.getMember()));
        }
        return byEmoji.entrySet().stream()
                .map(e -> new AuctionMessageResponse.ReactionSummary(e.getKey(), e.getValue()))
                .toList();
    }

    private static AuctionMessageResponse.MemberSummary toMemberSummary(Member member) {
        return member != null ? new AuctionMessageResponse.MemberSummary(member.getId(), member.getNickname()) : null;
    }
}
