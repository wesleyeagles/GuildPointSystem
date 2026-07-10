package com.guild.app.auction.repository;

import com.guild.app.auction.entity.Auction;
import com.guild.app.common.enums.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    List<Auction> findByStatusInOrderByCreatedAtDesc(List<AuctionStatus> statuses);

    List<Auction> findByStatusOrderByCreatedAtDesc(AuctionStatus status);

    @Query("SELECT a FROM Auction a LEFT JOIN FETCH a.winner WHERE a.id = :id")
    Optional<Auction> findByIdWithWinner(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Auction a WHERE a.id = :id")
    Optional<Auction> findByIdForUpdate(Long id);

    List<Auction> findByStatus(AuctionStatus status);
}
