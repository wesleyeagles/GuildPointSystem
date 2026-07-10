package com.guild.app.item.validation;

import com.guild.app.common.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;

public final class TalicValidator {

    private static final Set<String> ELEMENTAL = Set.of(
            "SACREDFIRE", "BELIEF", "GUARD", "GLORY");

    private static final Set<String> ARMOR_EXCLUSIVE = Set.of(
            "WISDOM", "GRACE", "DARKNESS", "MERCY");

    private TalicValidator() {}

    public static void validateWeaponTalics(List<TalicInput> talics) {
        long elemental = talics.stream().filter(t -> ELEMENTAL.contains(t.type().toUpperCase())).count();
        if (elemental > 1) {
            throw new AppException("Weapon allows at most 1 elemental talic", HttpStatus.BAD_REQUEST);
        }
        for (var t : talics) {
            validateLevel(t.level());
        }
    }

    public static void validateArmorTalics(String subtype, List<TalicInput> talics) {
        for (var t : talics) {
            validateLevel(t.level());
            String type = t.type().toUpperCase();
            if ("WISDOM".equals(type) && !"Helmet".equalsIgnoreCase(subtype)) {
                throw new AppException("Wisdom talic only for Helmet", HttpStatus.BAD_REQUEST);
            }
            if (("GRACE".equals(type) || "DARKNESS".equals(type)) && !"Gloves".equalsIgnoreCase(subtype)) {
                throw new AppException("Grace/Darkness talic only for Gloves", HttpStatus.BAD_REQUEST);
            }
            if ("MERCY".equals(type) && !"Shoes".equalsIgnoreCase(subtype)) {
                throw new AppException("Mercy talic only for Shoes", HttpStatus.BAD_REQUEST);
            }
        }
        long exclusive = talics.stream()
                .filter(t -> ARMOR_EXCLUSIVE.contains(t.type().toUpperCase())).count();
        if (exclusive > 1) {
            throw new AppException("Armor allows at most 1 exclusive subtype talic", HttpStatus.BAD_REQUEST);
        }
    }

    private static void validateLevel(int level) {
        if (level < 0 || level > 7) {
            throw new AppException("Talic level must be 0-7", HttpStatus.BAD_REQUEST);
        }
    }

    public record TalicInput(String type, int level, int slot) {}
}
