package com.guild.app.item.repository;

import com.guild.app.item.entity.ItemSeed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemSeedRepository extends JpaRepository<ItemSeed, Long> {

    List<ItemSeed> findByCategoryOrderBySortOrderAscNameAsc(String category);
}
