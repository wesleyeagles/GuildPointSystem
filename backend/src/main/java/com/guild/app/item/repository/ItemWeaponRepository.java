package com.guild.app.item.repository;

import com.guild.app.item.entity.ItemWeapon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemWeaponRepository extends JpaRepository<ItemWeapon, Long> {
}
