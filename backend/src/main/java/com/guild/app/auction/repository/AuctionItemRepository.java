package com.guild.app.auction.repository;

import com.guild.app.auction.entity.AuctionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuctionItemRepository extends JpaRepository<AuctionItem, Long> {

    @Query("SELECT ai FROM AuctionItem ai JOIN FETCH ai.item WHERE ai.auction.id = :auctionId")
    List<AuctionItem> findByAuctionIdWithItem(@Param("auctionId") Long auctionId);
}
