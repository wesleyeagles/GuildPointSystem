package com.guild.app.auction.repository;

import com.guild.app.auction.entity.AuctionBid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.guild.app.common.enums.AuctionStatus;

import java.util.Collection;
import java.util.List;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long> {

    /** Members holding the given amount, in the order they reached it (stable roulette order). */
    @Query("""
            SELECT b.member.id FROM AuctionBid b
            WHERE b.auction.id = :auctionId AND b.cancelledAt IS NULL AND b.amount = :amount
            GROUP BY b.member.id
            ORDER BY MIN(b.id) ASC
            """)
    List<Long> findTiedMemberIds(Long auctionId, Long amount);

    /** Auctions in these statuses where the member has placed at least one bid. */
    @Query("""
            SELECT DISTINCT b.auction.id FROM AuctionBid b
            WHERE b.cancelledAt IS NULL AND b.member.id = :memberId AND b.auction.status IN :statuses
            """)
    List<Long> findAuctionIdsBidBy(Long memberId, Collection<AuctionStatus> statuses);

    /** Other auctions in these statuses where the member holds the top bid (alone or tied). */
    @Query("""
            SELECT DISTINCT b.auction.id FROM AuctionBid b
            WHERE b.cancelledAt IS NULL AND b.member.id = :memberId AND b.auction.id <> :auctionId AND b.auction.status IN :statuses
              AND b.auction.currentBid > 0 AND b.amount = b.auction.currentBid
            """)
    List<Long> findOtherAuctionsLeadBy(Long memberId, Long auctionId, Collection<AuctionStatus> statuses);

    /** Latest bids of the auction with the bidder, newest first (history in the game window). */
    @Query("SELECT b FROM AuctionBid b JOIN FETCH b.member WHERE b.auction.id = :auctionId AND b.cancelledAt IS NULL ORDER BY b.id DESC")
    List<AuctionBid> findRecent(Long auctionId, Pageable page);

    /** Latest bid of the auction (null = none): identifies the bid that outbid someone. */
    @Query("SELECT MAX(b.id) FROM AuctionBid b WHERE b.auction.id = :auctionId AND b.cancelledAt IS NULL")
    Long findLatestBidId(Long auctionId);

    @Query("SELECT COALESCE(MAX(b.amount), 0) FROM AuctionBid b WHERE b.auction.id = :auctionId"
            + " AND b.cancelledAt IS NULL AND b.id <> :bidId")
    long highestWithout(Long auctionId, Long bidId);

    @Query("SELECT b.member.id FROM AuctionBid b WHERE b.auction.id = :auctionId"
            + " AND b.cancelledAt IS NULL AND b.id <> :bidId AND b.amount = :amount"
            + " GROUP BY b.member.id ORDER BY MIN(b.id)")
    List<Long> leadersWithout(Long auctionId, Long bidId, Long amount);

    @EntityGraph(attributePaths = {"member", "cancelledBy"})
    Page<AuctionBid> findByAuctionIdOrderByIdDesc(Long auctionId, Pageable page);
}
