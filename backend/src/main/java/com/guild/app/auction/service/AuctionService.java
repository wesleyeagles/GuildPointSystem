package com.guild.app.auction.service;

import com.guild.app.auction.dto.*;
import com.guild.app.auction.entity.*;
import com.guild.app.auction.repository.*;
import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.AuctionStatus;
import com.guild.app.common.enums.PointsModality;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(1, 5, 15, 30, 60, 180, 360, 720, 1440);

    private final AuctionRepository auctionRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final AuctionMessageRepository auctionMessageRepository;
    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;
    private final PointsService pointsService;
    private final AuditLogService auditLogService;
    private final WebSocketPublisher webSocketPublisher;

    private static final int CLOSED_LIST_LIMIT = 50;

    @Transactional(readOnly = true)
    public List<AuctionResponse> listOpen() {
        var active = auctionRepository.findByStatusInOrderByCreatedAtDesc(
                List.of(AuctionStatus.OPEN, AuctionStatus.DOLE, AuctionStatus.TIE_BREAK));
        var closed = auctionRepository.findByStatusOrderByCreatedAtDesc(AuctionStatus.CLOSED).stream()
                .limit(CLOSED_LIST_LIMIT)
                .toList();

        var combined = new ArrayList<Auction>(active.size() + closed.size());
        combined.addAll(active);
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

        var auction = new Auction();
        auction.setDurationMinutes(request.durationMinutes());
        auction.setCreatedBy(memberRepository.getReferenceById(actor.getId()));
        auction.setEndsAt(Instant.now().plusSeconds(request.durationMinutes() * 60L));
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

        auditLogService.log(AuditLogType.AUCTION_CREATED,
                memberRepository.getReferenceById(actor.getId()), null,
                Map.of("auctionId", auction.getId(), "durationMinutes", request.durationMinutes()));

        var response = toResponse(auction);
        publishAuctionUpdate(auction);
        return response;
    }

    @Transactional
    public AuctionResponse placeBid(Long auctionId, PlaceBidRequest request, MemberPrincipal actor) {
        var auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AppException("Leilão não encontrado.", HttpStatus.NOT_FOUND));

        if (auction.getStatus() != AuctionStatus.OPEN && auction.getStatus() != AuctionStatus.DOLE) {
            throw new AppException("Este leilão não está aceitando lances.", HttpStatus.BAD_REQUEST);
        }
        if (auction.getEndsAt().isBefore(Instant.now()) && auction.getStatus() == AuctionStatus.OPEN) {
            throw new AppException("Este leilão já expirou.", HttpStatus.BAD_REQUEST);
        }

        long available = pointsService.getAvailablePoints(actor.getId());
        long amount = request.amount();
        long currentBid = auction.getCurrentBid();

        if (amount > available) {
            throw new AppException("Saldo disponível insuficiente para este lance.", HttpStatus.BAD_REQUEST);
        }

        if (amount > currentBid) {
            // valid — must not exceed available (already checked)
        } else if (amount == currentBid) {
            if (amount != available) {
                throw new AppException("O lance deve ser maior que o atual, ou igual ao seu saldo disponível total.", HttpStatus.BAD_REQUEST);
            }
        } else {
            throw new AppException("O lance deve ser pelo menos igual ao lance atual.", HttpStatus.BAD_REQUEST);
        }

        var member = memberRepository.getReferenceById(actor.getId());
        var bid = new AuctionBid();
        bid.setAuction(auction);
        bid.setMember(member);
        bid.setAmount(amount);
        auctionBidRepository.save(bid);

        auction.setCurrentBid(amount);

        long secondsLeft = auction.getEndsAt().getEpochSecond() - Instant.now().getEpochSecond();
        if (auction.getStatus() == AuctionStatus.DOLE) {
            // Bid rescued the auction from DOLE — go back to OPEN with +10s
            auction.setEndsAt(Instant.now().plusSeconds(10));
            auction.setStatus(AuctionStatus.OPEN);
            auction.setDolePhase(0);
        } else if (secondsLeft <= 15) {
            auction.setEndsAt(Instant.now().plusSeconds(25));
        }

        auctionRepository.save(auction);

        addBidMessage(auction, member, amount);
        publishAuctionUpdate(auction);
        publishPointsUpdate(actor.getId());
        notifyOutbidMembers(auctionId, actor.getId(), amount);

        return toResponse(auction);
    }

    @Transactional
    public void sendChatMessage(Long auctionId, ChatMessageRequest request, MemberPrincipal actor) {
        var auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AppException("Leilão não encontrado.", HttpStatus.NOT_FOUND));
        if (auction.getStatus() == AuctionStatus.CLOSED) {
            throw new AppException("Este leilão já foi encerrado.", HttpStatus.BAD_REQUEST);
        }
        var msg = new AuctionMessage();
        msg.setAuction(auction);
        msg.setMember(memberRepository.getReferenceById(actor.getId()));
        msg.setType("TEXT");
        msg.setContent(request.content());
        msg = auctionMessageRepository.save(msg);
        webSocketPublisher.publishAuction(auctionId, Map.of(
                "type", "CHAT", "message", toMessageResponse(msg)));
    }

    @Transactional(readOnly = true)
    public List<AuctionMessageResponse> getMessages(Long auctionId) {
        return auctionMessageRepository.findByAuctionIdOrderByCreatedAtAsc(auctionId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional
    public void processExpiredAuctions() {
        var now = Instant.now();
        var openAuctions = auctionRepository.findByStatus(AuctionStatus.OPEN);
        for (var auction : openAuctions) {
            if (auction.getEndsAt().isAfter(now)) continue;
            startDoleSequence(auction);
        }

        var doleAuctions = auctionRepository.findByStatus(AuctionStatus.DOLE);
        for (var auction : doleAuctions) {
            if (auction.getEndsAt().isAfter(now)) continue;
            advanceDole(auction);
        }

        var tieBreakAuctions = auctionRepository.findByStatus(AuctionStatus.TIE_BREAK);
        for (var auction : tieBreakAuctions) {
            if (auction.getEndsAt().isAfter(now)) continue;
            finalizeTieBreak(auction);
        }
    }

    @Transactional
    protected void startDoleSequence(Auction auction) {
        auction = auctionRepository.findByIdForUpdate(auction.getId()).orElseThrow();
        // Guard: another scheduler tick may have already handled this
        if (auction.getStatus() != AuctionStatus.OPEN) return;
        if (auction.getEndsAt().isAfter(Instant.now())) return;
        if (auction.getCurrentBid() == 0) {
            closeWithoutWinner(auction);
            return;
        }
        auction.setStatus(AuctionStatus.DOLE);
        auction.setDolePhase(1);
        auction.setEndsAt(Instant.now().plusSeconds(2));
        auctionRepository.save(auction);
        addBotMessage(auction, "DOLE 1");
        publishAuctionUpdate(auction);
    }

    @Transactional
    protected void advanceDole(Auction auction) {
        auction = auctionRepository.findByIdForUpdate(auction.getId()).orElseThrow();
        // Guard: a bid may have reset the status, or another tick already advanced
        if (auction.getStatus() != AuctionStatus.DOLE) return;
        if (auction.getEndsAt().isAfter(Instant.now())) return;
        int phase = auction.getDolePhase();
        if (phase >= 3) {
            resolveAuction(auction);
            return;
        }
        phase++;
        auction.setDolePhase(phase);
        auction.setEndsAt(Instant.now().plusSeconds(2));
        auctionRepository.save(auction);
        addBotMessage(auction, "DOLE " + phase);
        publishAuctionUpdate(auction);
    }

    @Transactional
    protected void resolveAuction(Auction auction) {
        var tiedIds = auctionBidRepository.findTiedMemberIds(auction.getId(), auction.getCurrentBid());
        if (tiedIds.size() <= 1) {
            var winnerId = tiedIds.isEmpty() ? null : tiedIds.getFirst();
            closeWithWinner(auction, winnerId);
        } else {
            auction.setStatus(AuctionStatus.TIE_BREAK);
            auction.setTieBreakSeed(ThreadLocalRandom.current().nextLong());
            auction.setEndsAt(Instant.now().plusSeconds(15));
            auctionRepository.save(auction);

            var memberNicknameMap = memberRepository.findAllById(tiedIds).stream()
                    .collect(Collectors.toMap(Member::getId, Member::getNickname));
            var tiedMembers = tiedIds.stream()
                    .map(mid -> Map.of("id", mid, "nickname", memberNicknameMap.getOrDefault(mid, "?")))
                    .toList();

            webSocketPublisher.publishAuction(auction.getId(), Map.of(
                    "type", "TIE_BREAK_START",
                    "tiedMemberIds", tiedIds,
                    "tiedMembers", tiedMembers,
                    "seed", auction.getTieBreakSeed()));
            publishAuctionUpdate(auction);
        }
    }

    @Transactional
    protected void finalizeTieBreak(Auction auction) {
        auction = auctionRepository.findByIdForUpdate(auction.getId()).orElseThrow();
        if (auction.getStatus() != AuctionStatus.TIE_BREAK) return;
        if (auction.getEndsAt().isAfter(Instant.now())) return;
        var tiedIds = auctionBidRepository.findTiedMemberIds(auction.getId(), auction.getCurrentBid());
        if (tiedIds.isEmpty()) {
            closeWithoutWinner(auction);
            return;
        }
        int winnerIndex = (int) Math.floorMod(auction.getTieBreakSeed(), tiedIds.size());
        closeWithWinner(auction, tiedIds.get(winnerIndex));
    }

    @Transactional
    protected void closeWithWinner(Auction auction, Long winnerId) {
        auction.setStatus(AuctionStatus.CLOSED);
        auction.setWinner(winnerId != null ? memberRepository.getReferenceById(winnerId) : null);
        auctionRepository.save(auction);

        if (winnerId != null) {
            var winner = memberRepository.findByIdForUpdate(winnerId).orElseThrow();
            var actor = memberRepository.getReferenceById(winnerId);
            pointsService.adjustPoints(winner, actor, -auction.getCurrentBid(),
                    PointsModality.LEILAO, "Auction win #" + auction.getId());
            publishPointsUpdate(winnerId);
        }

        auditLogService.log(AuditLogType.AUCTION_CLOSED, auction.getCreatedBy(), null, Map.of(
                "auctionId", auction.getId(),
                "winnerId", winnerId != null ? winnerId : "none",
                "amount", auction.getCurrentBid()));

        webSocketPublisher.publishAuction(auction.getId(), Map.of(
                "type", "AUCTION_CLOSED",
                "winnerId", winnerId != null ? winnerId : "none",
                "amount", auction.getCurrentBid()));
        publishAuctionUpdate(auction);
    }

    @Transactional
    protected void closeWithoutWinner(Auction auction) {
        closeWithWinner(auction, null);
    }

    private void addBidMessage(Auction auction, Member member, long amount) {
        var msg = new AuctionMessage();
        msg.setAuction(auction);
        msg.setMember(member);
        msg.setType("BID");
        msg.setContent(member.getNickname() + " deu um lance de " + amount + " pts");
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

    private void notifyOutbidMembers(Long auctionId, Long bidderId, long amount) {
        var previousBids = auctionBidRepository.findTopBidsForAuction(auctionId);
        previousBids.stream()
                .filter(b -> !b.getMember().getId().equals(bidderId) && b.getAmount() < amount)
                .map(b -> b.getMember().getId())
                .distinct()
                .forEach(this::publishPointsUpdate);
    }

    private void publishAuctionUpdate(Auction auction) {
        var payload = Map.of("type", "AUCTION_UPDATE", "auction", toResponse(auction));
        webSocketPublisher.publishAuction(auction.getId(), payload);
        webSocketPublisher.publishAuctionList(payload);
    }

    private void publishPointsUpdate(Long memberId) {
        webSocketPublisher.publishPointsUpdate(memberId, Map.of(
                "memberId", memberId,
                "availablePoints", pointsService.getAvailablePoints(memberId)));
    }

    private AuctionResponse toResponse(Auction auction) {
        var items = auctionItemRepository.findByAuctionIdWithItem(auction.getId()).stream()
                .map(ai -> new AuctionResponse.AuctionItemResponse(
                        ai.getItem().getId(),
                        ai.getItem().getName(),
                        ai.getItem().getImageUrl(),
                        ai.getQuantity()))
                .toList();

        var tiedIds = auction.getCurrentBid() > 0
                ? auctionBidRepository.findTiedMemberIds(auction.getId(), auction.getCurrentBid())
                : List.<Long>of();

        boolean tied = tiedIds.size() > 1;

        List<String> tiedMemberNicknames;
        Long leaderId = null;
        String leaderNickname = null;

        if (tied) {
            var memberNicknameMap = memberRepository.findAllById(tiedIds).stream()
                    .collect(Collectors.toMap(Member::getId, Member::getNickname));
            tiedMemberNicknames = tiedIds.stream()
                    .map(id -> memberNicknameMap.getOrDefault(id, "?"))
                    .toList();
        } else {
            tiedMemberNicknames = List.of();
            var topBid = auctionBidRepository.findFirstByAuctionIdOrderByAmountDescCreatedAtAsc(auction.getId());
            leaderId = topBid.map(b -> b.getMember().getId()).orElse(null);
            leaderNickname = topBid.map(b -> b.getMember().getNickname()).orElse(null);
        }

        return new AuctionResponse(
                auction.getId(),
                auction.getStatus(),
                auction.getDurationMinutes(),
                auction.getCreatedAt(),
                auction.getEndsAt(),
                auction.getCurrentBid(),
                auction.getWinner() != null ? auction.getWinner().getId() : null,
                auction.getWinner() != null ? auction.getWinner().getNickname() : null,
                leaderId,
                leaderNickname,
                tiedIds.size(),
                tied,
                tiedIds,
                tiedMemberNicknames,
                items,
                auction.getTieBreakSeed());
    }

    private AuctionMessageResponse toMessageResponse(AuctionMessage msg) {
        var member = msg.getMember();
        return new AuctionMessageResponse(
                msg.getId(),
                msg.getType(),
                msg.getContent(),
                msg.getImageUrl(),
                msg.getCreatedAt(),
                member != null
                        ? new AuctionMessageResponse.MemberSummary(member.getId(), member.getNickname())
                        : null);
    }
}
