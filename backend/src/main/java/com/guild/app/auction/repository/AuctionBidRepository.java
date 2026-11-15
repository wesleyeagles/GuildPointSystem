package com.guild.app.auction.repository;

import com.guild.app.auction.entity.AuctionBid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.guild.app.common.enums.AuctionStatus;

import java.util.Collection;
import java.util.List;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long> {

    /** Members holding the given amount, in the order they reached it (stable roulette order). */
    @Query("""
            SELECT b.member.id FROM AuctionBid b
            WHERE b.auction.id = :auctionId AND b.amount = :amount
            GROUP BY b.member.id
            ORDER BY MIN(b.id) ASC
            """)
    List<Long> findTiedMemberIds(Long auctionId, Long amount);

    /** Auctions in these statuses where the member has placed at least one bid. */
    @Query("""
            SELECT DISTINCT b.auction.id FROM AuctionBid b
            WHERE b.member.id = :memberId AND b.auction.status IN :statuses
            """)
    List<Long> findAuctionIdsBidBy(Long memberId, Collection<AuctionStatus> statuses);

    /** Latest bid of the auction (null = none): identifies the bid that outbid someone. */
    @Query("SELECT MAX(b.id) FROM AuctionBid b WHERE b.auction.id = :auctionId")
    Long findLatestBidId(Long auctionId);
}
