package com.guild.app.auction.service;

import com.guild.app.auction.dto.AuctionResponse;
import com.guild.app.auction.repository.AuctionRepository;
import com.guild.app.common.enums.AuctionStatus;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Aviso de leilão aberto dentro do jogo, pela DLL (o card do evento, com "Ver leilão" no lugar da senha).
 * Como os outros envios da DLL, o membro é achado pelo nome do personagem (sem login), só se aprovado.
 * A DLL consulta a cada poucos segundos: os leilões abertos ficam em memória por {@link #CACHE}, e sem
 * leilão aberto a consulta nem procura o membro.
 */
@Service
@RequiredArgsConstructor
public class AuctionGameService {
    static final Duration CACHE = Duration.ofSeconds(3);
    /** Recebendo lances: pausado (relógio parado) e agendado não aparecem. */
    static final List<AuctionStatus> SHOWN = List.of(AuctionStatus.OPEN, AuctionStatus.DOLE, AuctionStatus.TIE_BREAK);

    private final AuctionRepository auctions;
    private final AuctionService auctionService;
    private final MemberRepository members;
    private volatile Snapshot snapshot = new Snapshot(List.of(), Instant.EPOCH);
    Clock clock = Clock.systemUTC();

    /** Leilão como a DLL mostra: fim em segundos Unix (a DLL conta pelo relógio do site). */
    public record GameAuction(long id, long endsAtEpoch, long currentBid, String items) {}

    private record Snapshot(List<GameAuction> auctions, Instant loadedAt) {}

    /** Leilões abertos, do mais novo ao mais antigo; vazio se o personagem não é de membro aprovado. */
    @Transactional(readOnly = true)
    public List<GameAuction> open(String character) {
        List<GameAuction> open = load(clock.instant());
        if (open.isEmpty() || !eligible(character)) return List.of();
        return open;
    }

    private List<GameAuction> load(Instant now) {
        Snapshot current = snapshot;
        if (now.isBefore(current.loadedAt().plus(CACHE)) && !now.isBefore(current.loadedAt())) return current.auctions();
        List<GameAuction> loaded = auctions.findByStatusInOrderByCreatedAtDesc(SHOWN).stream()
            .map(a -> auctionService.getById(a.getId()))
            .map(r -> new GameAuction(r.id(), r.endsAt().getEpochSecond(), r.currentBid() == null ? 0 : r.currentBid(),
                items(r)))
            .toList();
        snapshot = new Snapshot(loaded, now);
        return loaded;
    }

    /** "Nome xN, Nome" como o formatAuctionItems do site, numa linha só. */
    static String items(AuctionResponse auction) {
        if (auction.items() == null || auction.items().isEmpty()) return "Leilão #" + auction.id();
        String text = String.join(", ", auction.items().stream()
            .map(i -> i.quantity() > 1 ? i.itemName() + " x" + i.quantity() : i.itemName())
            .toList());
        text = text.replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ").replaceAll(" {2,}", " ").trim();
        return text.length() > 180 ? text.substring(0, 179) + "…" : text;
    }

    /** Testes: esquece os leilões guardados. */
    void forget() {
        snapshot = new Snapshot(List.of(), Instant.EPOCH);
    }

    private boolean eligible(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return false;
        return members.findByGameCharacter(character.strip()).stream().anyMatch(AuctionGameService::eligible);
    }

    private static boolean eligible(Member member) {
        return member.getStatus() == MemberStatus.APROVADO && member.isProfileComplete()
            && member.getDeletedAt() == null && member.getSuspendedAt() == null;
    }
}
