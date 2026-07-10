package com.guild.app.item.repository;

import com.guild.app.item.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByDeletedFalseOrderByCreatedAtDesc();
}
