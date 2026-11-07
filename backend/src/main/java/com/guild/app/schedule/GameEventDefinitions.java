package com.guild.app.schedule;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class GameEventDefinitions {

    public static final ZoneId SCHEDULE_ZONE = ZoneId.of("America/Sao_Paulo");
    /** Cerberus server time (Moscow, UTC+3): 00:00 there = 18:00 in Brasília. */
    public static final ZoneId SERVER_ZONE = ZoneId.of("Europe/Moscow");
    public static final long TEN_MINUTE_WARNING_MS = 10 * 60 * 1000L;

    private GameEventDefinitions() {}

    public sealed interface GameEvent permits DailyGameEvent, MultiDailyGameEvent, IntervalGameEvent {
        String id();

        String label();
    }

    public record DailyGameEvent(String id, String label, int hour, int minute, ZoneId zone)
            implements GameEvent {
        public DailyGameEvent(String id, String label, int hour, int minute) {
            this(id, label, hour, minute, SCHEDULE_ZONE);
        }
    }

    /** Area of the boss rotation a PB belongs to (QG = PB HQ, outpost = PB Colônia). */
    public enum RotationArea { QG, OUTPOST }

    /**
     * Several fixed times per day; counts down to the nearest one. With an {@code area}, only the
     * hours where the guild has bosses in that rotation area count (see {@link #forGuild}).
     */
    public record MultiDailyGameEvent(String id, String label, List<Integer> hours, int minute, ZoneId zone,
                                      RotationArea area) implements GameEvent {
        public MultiDailyGameEvent(String id, String label, List<Integer> hours, int minute, ZoneId zone) {
            this(id, label, hours, minute, zone, null);
        }
    }

    public record IntervalGameEvent(String id, String label, int intervalDays, ZonedDateTime anchor)
            implements GameEvent {}

    public static final List<GameEvent> ALL = List.of(
            // CW in server time: 06:00 / 14:00 / 22:00 (= 00:00 / 08:00 / 16:00 Brasília).
            new DailyGameEvent("cw1", "CW1", 6, 0, SERVER_ZONE),
            new DailyGameEvent("cw2", "CW2", 14, 0, SERVER_ZONE),
            new DailyGameEvent("cw3", "CW3", 22, 0, SERVER_ZONE),
            // PBs in Brasília time.
            // HQ and Colônia skip the hours where the guild has no boss in the rotation; Middle is not in it.
            new MultiDailyGameEvent("pb-hq", "PB HQ", List.of(2, 6, 10, 14, 18, 22), 0, SCHEDULE_ZONE, RotationArea.QG),
            new MultiDailyGameEvent("pb-colonia", "PB Colônia", List.of(2, 10, 18), 0, SCHEDULE_ZONE, RotationArea.OUTPOST),
            new MultiDailyGameEvent("pb-middle", "PB Middle", List.of(6, 18), 0, SCHEDULE_ZONE));

    /**
     * The event as the guild sees it: a rotation-bound PB keeps only the hours in {@code guildHours}
     * for its area, and disappears when none is left. {@code guildHours == null} (rotation unavailable)
     * keeps every hour, as before the rotation existed.
     */
    public static Optional<GameEvent> forGuild(GameEvent event, Map<RotationArea, Set<Integer>> guildHours) {
        if (!(event instanceof MultiDailyGameEvent multi) || multi.area() == null || guildHours == null) {
            return Optional.of(event);
        }
        var allowed = guildHours.getOrDefault(multi.area(), Set.of());
        var hours = multi.hours().stream().filter(allowed::contains).toList();
        if (hours.isEmpty()) return Optional.empty();
        return Optional.of(new MultiDailyGameEvent(multi.id(), multi.label(), hours, multi.minute(), multi.zone(), multi.area()));
    }
}
