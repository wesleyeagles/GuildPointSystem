package com.guild.app.auction.service;

import com.guild.app.auction.dto.AuctionResponse;
import com.guild.app.auction.entity.AuctionMessage;
import com.guild.app.auction.repository.AuctionBidRepository;
import com.guild.app.auction.repository.AuctionMessageRepository;
import com.guild.app.auction.repository.AuctionRepository;
import com.guild.app.common.exception.AppException;
import com.guild.app.points.service.PointsService;
import com.guild.app.common.enums.AuctionStatus;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.item.service.GameItemCard;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

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
    private final GameItemCard cards;
    private final AuctionBidRepository bids;
    private final AuctionMessageRepository messages;
    private final PointsService points;
    private volatile Snapshot snapshot = new Snapshot(List.of(), Instant.EPOCH);
    Clock clock = Clock.systemUTC();

    /** Leilão como a DLL mostra: fim em segundos Unix (a DLL conta pelo relógio do site). */
    public record GameAuction(long id, long endsAtEpoch, long currentBid, String items, String effects, String icon) {}

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
            .map(r -> {
                String code = firstCode(r);
                return new GameAuction(r.id(), r.endsAt().getEpochSecond(), r.currentBid() == null ? 0 : r.currentBid(),
                    items(r), cards.effects(code), iconKey(r));
            })
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

    /**
     * Ícone do card: o sprite do primeiro item do jogo; sem item do jogo, a imagem enviada no site do primeiro
     * item que tiver uma (png/jpg).
     */
    String iconKey(AuctionResponse auction) {
        String code = firstCode(auction);
        if (code != null) return cards.iconKey(code);
        if (auction.items() == null) return "";
        return auction.items().stream().map(i -> GameItemCard.uploadIconKey(i.imageUrl()))
            .filter(k -> !k.isEmpty()).findFirst().orElse("");
    }

    /** Código do primeiro item com ícone do jogo (é ele que aparece no card). */
    static String firstCode(AuctionResponse auction) {
        if (auction.items() == null) return null;
        return auction.items().stream().filter(i -> i.gameIcon() != null).map(i -> i.gameIcon().gameCode())
            .findFirst().orElse(null);
    }

    // ---- lance superado, lista e lance pelo jogo ----

    /** Lance mínimo para superar (igual ao site): o atual + 10 (o 1º lance, 10). All-in abaixo disso também vale. */
    static long minBid(long currentBid) {
        return Math.max(0, currentBid) + AuctionService.MIN_INCREMENT;
    }

    /** Status em que ainda dá para dar lance. */
    static final List<AuctionStatus> BIDDING = List.of(AuctionStatus.OPEN, AuctionStatus.DOLE);
    /** Lista do jogo: tudo que está em andamento (pausado e roleta aparecem, sem lance). */
    static final List<AuctionStatus> LISTED = AuctionService.ACTIVE_STATUSES;

    /**
     * Leilão em que o membro deu lance e outro passou na frente. {@code bidId} = último lance do leilão (o card
     * volta se for superado de novo depois de fechar). {@code available} é o saldo livre para este leilão.
     */
    public record Outbid(long bidId, long auctionId, long endsAtEpoch, long currentBid, long minBid, long available,
                         String items, String effects, String icon) {}

    /** Uma linha da lista do jogo; {@code mine}: 0 = não participa, 1 = superado, 2 = com o maior lance (ou empatado). */
    public record Row(long id, String status, long endsAtEpoch, long currentBid, long minBid, int mine, String leader,
                      String items, String effects, String icon) {}

    public record GameList(long available, List<Row> auctions) {}

    /** Mensagem do sistema/lance (sem o chat): segundos Unix, tipo (BID/BOT) e texto. */
    public record Message(long atEpoch, String type, String text) {}

    public record Detail(Row auction, long available, List<Message> messages) {}

    @Transactional(readOnly = true)
    public List<Outbid> outbid(String character) {
        Optional<Member> found = member(character);
        if (found.isEmpty()) return List.of();
        Member member = found.get();
        return bids.findAuctionIdsBidBy(member.getId(), BIDDING).stream()
            .map(auctionService::getById)
            .filter(r -> r.currentBid() != null && r.currentBid() > 0 && !leads(r, member.getId()))
            .sorted((a, b) -> Long.compare(b.id(), a.id()))
            .map(r -> {
                String code = firstCode(r);
                Long bidId = bids.findLatestBidId(r.id());
                return new Outbid(bidId == null ? 0 : bidId, r.id(), r.endsAt().getEpochSecond(), r.currentBid(),
                    minBid(r.currentBid()), points.getAvailablePoints(member, r.id()), items(r),
                    cards.effects(code), iconKey(r));
            })
            .filter(o -> o.bidId() > 0)
            .toList();
    }

    @Transactional(readOnly = true)
    public GameList list(String character) {
        Member member = member(character).orElseThrow(AuctionGameService::notMember);
        var mine = new HashSet<>(bids.findAuctionIdsBidBy(member.getId(), LISTED));
        List<Row> rows = auctions.findByStatusInOrderByCreatedAtDesc(LISTED).stream()
            .map(a -> row(auctionService.getById(a.getId()), member.getId(), mine.contains(a.getId())))
            .toList();
        return new GameList(points.getAvailablePoints(member, null), rows);
    }

    @Transactional(readOnly = true)
    public Detail detail(long auctionId, String character) {
        Member member = member(character).orElseThrow(AuctionGameService::notMember);
        AuctionResponse r = auctionService.getById(auctionId);
        if (!LISTED.contains(r.status())) throw new AppException("Este leilão não está em andamento.", HttpStatus.NOT_FOUND);
        boolean participates = bids.findAuctionIdsBidBy(member.getId(), LISTED).contains(auctionId);
        List<Message> list = messages.findTop30ByAuction_IdAndTypeInOrderByIdDesc(auctionId, List.of("BID", "BOT")).stream()
            .map(AuctionGameService::message)
            .toList();
        return new Detail(row(r, member.getId(), participates), points.getAvailablePoints(member, auctionId), list);
    }

    /** Lance pelo jogo: as mesmas regras do site (bloqueio de venda, saldo, +10, all-in...). */
    public AuctionResponse bid(long auctionId, String character, String body) {
        Member member = member(character).orElseThrow(AuctionGameService::notMember);
        String text = body == null ? "" : body.strip().replace(".", "");
        if (!text.matches("\\d{1,12}")) throw new AppException("Digite o valor do lance em pontos.", HttpStatus.BAD_REQUEST);
        return auctionService.placeBid(auctionId, Long.parseLong(text), member.getId());
    }

    private Row row(AuctionResponse r, Long memberId, boolean participates) {
        long current = r.currentBid() == null ? 0 : r.currentBid();
        int mine = leads(r, memberId) ? 2 : participates ? 1 : 0;
        String leader = r.tied() && r.tiedMemberNicknames() != null && r.tiedMemberNicknames().size() > 1
            ? String.join(", ", r.tiedMemberNicknames())
            : r.leaderNickname() == null ? "" : r.leaderNickname();
        long ends = r.status() == AuctionStatus.PAUSED && r.pausedRemainingMs() != null
            ? clock.instant().plusMillis(r.pausedRemainingMs()).getEpochSecond()
            : r.endsAt() == null ? 0 : r.endsAt().getEpochSecond();
        String code = firstCode(r);
        return new Row(r.id(), r.status().name(), ends, current, minBid(current), mine, clean(leader, 60), items(r),
            cards.effects(code), iconKey(r));
    }

    private static boolean leads(AuctionResponse r, Long memberId) {
        if (r.tiedMemberIds() != null && r.tiedMemberIds().contains(memberId)) return true;
        return memberId.equals(r.leaderId());
    }

    private static Message message(AuctionMessage m) {
        return new Message(m.getCreatedAt().getEpochSecond(), m.getType(), clean(m.getContent(), 200));
    }

    /** Uma linha só, sem TAB, cortada. */
    static String clean(String text, int max) {
        if (text == null) return "";
        String out = text.replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ").replaceAll(" {2,}", " ").trim();
        return out.length() > max ? out.substring(0, max - 1) + "…" : out;
    }

    private static AppException notMember() {
        return new AppException("Personagem sem conta aprovada no site.", HttpStatus.FORBIDDEN);
    }

    private Optional<Member> member(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        return members.findByGameCharacter(character.strip()).stream().filter(AuctionGameService::eligible).findFirst();
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
