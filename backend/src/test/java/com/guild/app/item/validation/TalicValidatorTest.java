package com.guild.app.item.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TalicValidatorTest {

    @Test
    void weapon_allowsKeenAndOneElemental() {
        assertDoesNotThrow(() -> TalicValidator.validateWeaponTalics(List.of(
                new TalicValidator.TalicInput("KEEN", 3, 0),
                new TalicValidator.TalicInput("SACREDFIRE", 2, 1))));
    }

    @Test
    void weapon_rejectsTwoElementals() {
        assertThrows(Exception.class, () -> TalicValidator.validateWeaponTalics(List.of(
                new TalicValidator.TalicInput("SACREDFIRE", 1, 0),
                new TalicValidator.TalicInput("BELIEF", 1, 1))));
    }

    @Test
    void armor_wisdomOnlyOnHelmet() {
        assertDoesNotThrow(() -> TalicValidator.validateArmorTalics("Helmet", List.of(
                new TalicValidator.TalicInput("FAVOR", 1, 0),
                new TalicValidator.TalicInput("WISDOM", 2, 1))));
        assertThrows(Exception.class, () -> TalicValidator.validateArmorTalics("Gloves", List.of(
                new TalicValidator.TalicInput("WISDOM", 1, 0))));
    }
}
