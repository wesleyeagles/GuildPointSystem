package com.guild.app.item.repository;

import com.guild.app.item.entity.ItemTalic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemTalicRepository extends JpaRepository<ItemTalic, Long> {

    List<ItemTalic> findByItemId(Long itemId);
}
