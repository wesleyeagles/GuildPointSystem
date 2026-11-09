package com.guild.app.schedule;

import com.guild.app.rotation.service.RotationService;
import com.guild.app.schedule.GameEventDefinitions.RotationArea;
import com.guild.app.schedule.dto.ScheduleTenMinuteAlertMessage;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.guild.app.schedule.GameEventDefinitions.ALL;
import static com.guild.app.schedule.GameEventDefinitions.SCHEDULE_ZONE;
import static com.guild.app.schedule.GameEventDefinitions.TEN_MINUTE_WARNING_MS;

@Component
@RequiredArgsConstructor
public class GameEventScheduleAlertScheduler {

    private static final long TICK_MS = 5_000L;

    private final GameEventScheduleCalculator calculator;
    private final WebSocketPublisher webSocketPublisher;
    private final ScheduleTestAlertState testAlertState;
    private final RotationService rotation;

    private final Set<String> sentKeys = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedRate = TICK_MS)
    public void checkTenMinuteWarnings() {
        ZonedDateTime now = ZonedDateTime.now(SCHEDULE_ZONE);
        Map<RotationArea, Set<Integer>> guildHours = guildHours();
        for (var definition : ALL) {
            var guildEvent = GameEventDefinitions.forGuild(definition, guildHours);
            if (guildEvent.isEmpty()) {
                continue;
            }
            var event = guildEvent.get();
            ZonedDateTime next = calculator.nextOccurrence(event, now);
            long msUntil = calculator.millisUntil(event, now);
            long warning = GameEventDefinitions.warningMs(event.id());
            if (msUntil > warning || msUntil <= 0) {
                continue;
            }
            String dedupeKey = event.id() + "|" + next.toInstant();
            if (!sentKeys.add(dedupeKey)) {
                continue;
            }
            webSocketPublisher.publishScheduleAlert(new ScheduleTenMinuteAlertMessage(
                    "SCHEDULE_TEN_MINUTE",
                    event.id(),
                    event.label(),
                    (int) (warning / 60_000),
                    next.toInstant().toString()));
        }
        checkTestAlert(now);
        pruneSentKeys(now);
    }

    /** PB HQ/Colônia só avisam nos horários com PB nosso; sem a rotação, avisa em todos (como antes). */
    private Map<RotationArea, Set<Integer>> guildHours() {
        try {
            return rotation.pbHours(RotationService.HOME_GUILD);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void checkTestAlert(ZonedDateTime now) {
        testAlertState.getTestEventStart().ifPresent(start -> {
            long msUntil = Duration.between(now, start).toMillis();
            if (msUntil > TEN_MINUTE_WARNING_MS || msUntil <= 0) {
                return;
            }
            String dedupeKey = "test-alert|" + start.toInstant();
            if (!sentKeys.add(dedupeKey)) {
                return;
            }
            webSocketPublisher.publishScheduleAlert(new ScheduleTenMinuteAlertMessage(
                    "SCHEDULE_TEN_MINUTE",
                    "test-alert",
                    "TESTE",
                    10,
                    start.toInstant().toString()));
        });
    }

    private void pruneSentKeys(ZonedDateTime now) {
        long nowMs = now.toInstant().toEpochMilli();
        sentKeys.removeIf(key -> {
            int sep = key.indexOf('|');
            if (sep < 0) {
                return true;
            }
            try {
                long occurrenceMs = java.time.Instant.parse(key.substring(sep + 1)).toEpochMilli();
                return occurrenceMs < nowMs - MS_PER_DAY;
            } catch (Exception e) {
                return true;
            }
        });
    }

    private static final long MS_PER_DAY = 24 * 60 * 60 * 1000L;
}
