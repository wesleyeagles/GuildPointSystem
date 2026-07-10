package com.guild.app.item.repository;

import com.guild.app.item.entity.ItemAccessory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ItemAccessoryRepository extends JpaRepository<ItemAccessory, Long> {

    @Query("SELECT a FROM ItemAccessory a JOIN FETCH a.race WHERE a.itemId = :itemId")
    Optional<ItemAccessory> findByIdWithRace(@Param("itemId") Long itemId);
}
