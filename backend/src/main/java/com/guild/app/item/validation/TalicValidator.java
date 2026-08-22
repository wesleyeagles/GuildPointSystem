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
            throw new AppException("Armas permitem no máximo 1 talic elemental.", HttpStatus.BAD_REQUEST);
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
                throw new AppException("Talic Wisdom só pode ser usado em capacete.", HttpStatus.BAD_REQUEST);
            }
            if (("GRACE".equals(type) || "DARKNESS".equals(type)) && !"Gloves".equalsIgnoreCase(subtype)) {
                throw new AppException("Talics Grace/Darkness só podem ser usados em luvas.", HttpStatus.BAD_REQUEST);
            }
            if ("MERCY".equals(type) && !"Shoes".equalsIgnoreCase(subtype)) {
                throw new AppException("Talic Mercy só pode ser usado em sapatos.", HttpStatus.BAD_REQUEST);
            }
        }
        long exclusive = talics.stream()
                .filter(t -> ARMOR_EXCLUSIVE.contains(t.type().toUpperCase())).count();
        if (exclusive > 1) {
            throw new AppException("Armaduras permitem no máximo 1 talic exclusivo por subtipo.", HttpStatus.BAD_REQUEST);
        }
    }

    private static void validateLevel(int level) {
        if (level < 0 || level > 7) {
            throw new AppException("O nível do talic deve ser entre 0 e 7.", HttpStatus.BAD_REQUEST);
        }
    }

    public record TalicInput(String type, int level, int slot) {}
}
