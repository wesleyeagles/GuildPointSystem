package com.guild.app.item.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.ItemType;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.item.dto.CreateItemRequest;
import com.guild.app.item.dto.ItemResponse;
import com.guild.app.item.entity.*;
import com.guild.app.item.repository.*;
import com.guild.app.item.validation.TalicValidator;
import com.guild.app.member.repository.GameRaceRepository;
import com.guild.app.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemWeaponRepository itemWeaponRepository;
    private final ItemArmorRepository itemArmorRepository;
    private final ItemAccessoryRepository itemAccessoryRepository;
    private final ItemMiscRepository itemMiscRepository;
    private final ItemTalicRepository itemTalicRepository;
    private final GameRaceRepository gameRaceRepository;
    private final MemberRepository memberRepository;

    public List<ItemResponse> list() {
        return itemRepository.findByDeletedFalseOrderByCreatedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    public ItemResponse getById(Long id) {
        var item = itemRepository.findById(id)
                .orElseThrow(() -> new AppException("Item not found", HttpStatus.NOT_FOUND));
        return toResponse(item);
    }

    @Transactional
    public ItemResponse create(CreateItemRequest request, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        if (request.imageUrl() == null || request.imageUrl().isBlank()) {
            throw new AppException("Image required", HttpStatus.BAD_REQUEST);
        }

        var item = new Item();
        item.setType(request.type());
        item.setName(request.name());
        item.setRarity(request.rarity());
        item.setImageUrl(request.imageUrl());
        item.setDescription(request.description());
        item.setCreatedBy(memberRepository.getReferenceById(actor.getId()));
        item = itemRepository.save(item);

        switch (request.type()) {
            case WEAPON -> createWeapon(item, request);
            case ARMOR -> createArmor(item, request);
            case ACCESSORY -> createAccessory(item, request);
            case MISC -> createMisc(item);
        }

        if (request.talics() != null && !request.talics().isEmpty()) {
            var talicInputs = request.talics().stream()
                    .map(t -> new TalicValidator.TalicInput(t.talicType(), t.level(), t.slot()))
                    .toList();
            if (request.type() == ItemType.WEAPON) {
                TalicValidator.validateWeaponTalics(talicInputs);
            } else if (request.type() == ItemType.ARMOR && request.armor() != null) {
                TalicValidator.validateArmorTalics(request.armor().subtype(), talicInputs);
            }
            for (var t : request.talics()) {
                var talic = new ItemTalic();
                talic.setItem(item);
                talic.setTalicType(t.talicType());
                talic.setLevel(t.level());
                talic.setSlot(t.slot());
                itemTalicRepository.save(talic);
            }
        }

        return toResponse(item);
    }

    private void createWeapon(Item item, CreateItemRequest request) {
        if (request.weapon() == null) throw new AppException("Weapon data required", HttpStatus.BAD_REQUEST);
        var w = request.weapon();
        var weapon = new ItemWeapon();
        weapon.setItem(item);
        weapon.setLevel(w.level() != null ? w.level() : 0);
        weapon.setSubtype(w.subtype());
        weapon.setAttackMin(w.attackMin() != null ? w.attackMin() : 0);
        weapon.setAttackMax(w.attackMax() != null ? w.attackMax() : 0);
        weapon.setForceAttackMin(w.forceAttackMin() != null ? w.forceAttackMin() : 0);
        weapon.setForceAttackMax(w.forceAttackMax() != null ? w.forceAttackMax() : 0);
        weapon.setCastName(w.castName());
        weapon.setSpecialEffects(w.specialEffects() != null ? w.specialEffects().stream().limit(4).toList() : List.of());
        itemWeaponRepository.save(weapon);
    }

    private void createArmor(Item item, CreateItemRequest request) {
        if (request.armor() == null) throw new AppException("Armor data required", HttpStatus.BAD_REQUEST);
        var a = request.armor();
        var armor = new ItemArmor();
        armor.setItem(item);
        armor.setLevel(a.level() != null ? a.level() : 0);
        armor.setSubtype(a.subtype());
        armor.setArmorClass(a.armorClass());
        armor.setAvgDefPower(a.avgDefPower() != null ? a.avgDefPower() : 0);
        armor.setDefenseSuccessRate(a.defenseSuccessRate() != null ? a.defenseSuccessRate() : 0);
        armor.setSpecialEffects(a.specialEffects() != null ? a.specialEffects().stream().limit(4).toList() : List.of());
        itemArmorRepository.save(armor);
    }

    private void createAccessory(Item item, CreateItemRequest request) {
        if (request.accessory() == null) throw new AppException("Accessory data required", HttpStatus.BAD_REQUEST);
        var a = request.accessory();
        var race = gameRaceRepository.findById(a.raceId())
                .orElseThrow(() -> new AppException("Race not found", HttpStatus.BAD_REQUEST));
        var accessory = new ItemAccessory();
        accessory.setItem(item);
        accessory.setRace(race);
        accessory.setSubtype(a.subtype());
        accessory.setSpecialEffects(a.specialEffects() != null ? a.specialEffects().stream().limit(4).toList() : List.of());
        itemAccessoryRepository.save(accessory);
    }

    private void createMisc(Item item) {
        var misc = new ItemMisc();
        misc.setItem(item);
        itemMiscRepository.save(misc);
    }

    private ItemResponse toResponse(Item item) {
        Object details = switch (item.getType()) {
            case WEAPON -> itemWeaponRepository.findById(item.getId())
                    .map(w -> new ItemResponse.WeaponDetails(
                            w.getLevel(), w.getSubtype(),
                            w.getAttackMin(), w.getAttackMax(),
                            w.getForceAttackMin(), w.getForceAttackMax(),
                            w.getCastName(), w.getSpecialEffects()))
                    .orElse(null);
            case ARMOR -> itemArmorRepository.findById(item.getId())
                    .map(a -> new ItemResponse.ArmorDetails(
                            a.getLevel(), a.getSubtype(), a.getArmorClass(),
                            a.getAvgDefPower(), a.getDefenseSuccessRate(),
                            a.getSpecialEffects()))
                    .orElse(null);
            case ACCESSORY -> itemAccessoryRepository.findByIdWithRace(item.getId())
                    .map(a -> new ItemResponse.AccessoryDetails(
                            a.getRace().getId(), a.getRace().getName(),
                            a.getSubtype(), a.getSpecialEffects()))
                    .orElse(null);
            case MISC -> null;
        };
        var talics = itemTalicRepository.findByItemId(item.getId()).stream()
                .map(t -> new ItemResponse.TalicResponse(t.getTalicType(), t.getLevel(), t.getSlot()))
                .toList();
        return new ItemResponse(item.getId(), item.getType(), item.getName(), item.getRarity(),
                item.getImageUrl(), item.getDescription(), details, talics, item.getCreatedAt());
    }
}
