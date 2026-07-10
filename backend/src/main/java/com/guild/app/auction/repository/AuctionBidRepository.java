package com.guild.app.auction.repository;

import com.guild.app.auction.entity.AuctionBid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long> {

    @Query("""
            SELECT b FROM AuctionBid b WHERE b.auction.id = :auctionId
            ORDER BY b.amount DESC, b.createdAt ASC
            """)
    List<AuctionBid> findTopBidsForAuction(Long auctionId);

    Optional<AuctionBid> findFirstByAuctionIdOrderByAmountDescCreatedAtAsc(Long auctionId);

    @Query("""
            SELECT DISTINCT b.member.id FROM AuctionBid b
            WHERE b.auction.id = :auctionId AND b.amount = :amount
            """)
    List<Long> findTiedMemberIds(Long auctionId, Long amount);
}
