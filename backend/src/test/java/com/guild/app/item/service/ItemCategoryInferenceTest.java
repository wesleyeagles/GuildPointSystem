package com.guild.app.item.service;

import com.guild.app.common.enums.ItemType;
import com.guild.app.item.dto.CreateItemRequest.AccessoryData;
import com.guild.app.item.dto.CreateItemRequest.ArmorData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemCategoryInferenceTest {

    private static ArmorData armor(String subtype) {
        return new ArmorData(50, subtype, "Warrior", 100, 10, List.of());
    }

    private static AccessoryData accessory(String subtype) {
        return new AccessoryData(1L, subtype, List.of());
    }

    @Test
    void handMadeItemsGetTheirTab() {
        assertThat(ItemService.inferCategory(ItemType.WEAPON, null, null)).isEqualTo("WEAPON");
        assertThat(ItemService.inferCategory(ItemType.ARMOR, armor("Helmet"), null)).isEqualTo("HELMET");
        assertThat(ItemService.inferCategory(ItemType.ARMOR, armor(" gloves "), null)).isEqualTo("GLOVES");
        assertThat(ItemService.inferCategory(ItemType.ACCESSORY, null, accessory("Ring"))).isEqualTo("RING");
        assertThat(ItemService.inferCategory(ItemType.ACCESSORY, null, accessory("AMULET"))).isEqualTo("AMULET");
    }

    @Test
    void unknownSubtypeOrMiscGoesToOther() {
        assertThat(ItemService.inferCategory(ItemType.MISC, null, null)).isNull();
        assertThat(ItemService.inferCategory(ItemType.ARMOR, armor("Cape"), null)).isNull();
        assertThat(ItemService.inferCategory(ItemType.ARMOR, null, null)).isNull();
        assertThat(ItemService.inferCategory(null, null, null)).isNull();
    }
}
