package com.guild.app.item.util;

import java.util.List;

public final class CivilMaskUtil {

    private static final String BELLATO = "11000000";
    private static final String CORA = "00110000";
    private static final String ACCRETIA = "00001000";
    private static final String BELLATO_CORA = "11110000";

    private static final List<String> ALL_STORED_FORMS = List.of(
            "11111", "11111000",
            "11110", "11110000",
            "11000", "11000000",
            "00110", "00110000",
            "00001", "00001000");

    private CivilMaskUtil() {}

    public static String normalizeTo8(String civilMask) {
        if (civilMask == null || civilMask.isBlank()) {
            return "";
        }
        var trimmed = civilMask.trim();
        if (trimmed.length() >= 8) {
            return trimmed.substring(0, 8);
        }
        return trimmed + "0".repeat(8 - trimmed.length());
    }

    /** Legacy 8-char masks and compact 5-char forms are equivalent in catalog data. */
    public static String alternateForm(String civilMask) {
        if (civilMask == null || civilMask.isBlank()) {
            return null;
        }
        return switch (civilMask.trim()) {
            case "11111" -> "11111000";
            case "11111000" -> "11111";
            case "11110" -> "11110000";
            case "11110000" -> "11110";
            case "00001" -> "00001000";
            case "00001000" -> "00001";
            case "11000" -> "11000000";
            case "11000000" -> "11000";
            case "00110" -> "00110000";
            case "00110000" -> "00110";
            default -> null;
        };
    }

    /** Race filter matches items usable by that race (or race combo), not exact civil_mask only. */
    public static boolean itemMatchesCivilFilter(String itemCivilMask, String filterCivilMask) {
        if (filterCivilMask == null || filterCivilMask.isBlank()) {
            return true;
        }
        var item8 = normalizeTo8(itemCivilMask);
        var filter8 = normalizeTo8(filterCivilMask);
        return switch (filter8) {
            case BELLATO -> includesRace(item8, BELLATO);
            case CORA -> includesRace(item8, CORA);
            case ACCRETIA -> includesRace(item8, ACCRETIA);
            case BELLATO_CORA -> includesRace(item8, BELLATO) && includesRace(item8, CORA);
            default -> item8.equals(filter8);
        };
    }

    public static List<String> storedMasksForFilter(String filterCivilMask) {
        if (filterCivilMask == null || filterCivilMask.isBlank()) {
            return List.of();
        }
        return ALL_STORED_FORMS.stream()
                .filter(mask -> itemMatchesCivilFilter(mask, filterCivilMask))
                .toList();
    }

    private static boolean includesRace(String itemMask8, String racePattern8) {
        for (int i = 0; i < 8; i++) {
            if (racePattern8.charAt(i) == '1' && itemMask8.charAt(i) != '1') {
                return false;
            }
        }
        return true;
    }
}
