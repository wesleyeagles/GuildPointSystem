package com.guild.app.schedule;

import com.guild.app.schedule.dto.ScheduleTenMinuteAlertMessage;
import com.guild.app.websocket.WebSocketPublisher;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.guild.app.schedule.GameEventDefinitions.SCHEDULE_ZONE;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleTestAlertState {

    private final ScheduleTestAlertProperties properties;
    private final WebSocketPublisher webSocketPublisher;
    private final TaskScheduler taskScheduler;

    @Getter
    private ZonedDateTime testEventStartsAt;

    @PostConstruct
    void init() {
        if (!properties.isEnabled()) {
            return;
        }
        int lead = Math.max(5, properties.getLeadSeconds());
        testEventStartsAt =
                ZonedDateTime.now(SCHEDULE_ZONE).plusMinutes(10).plusSeconds(lead);
        log.info(
                "Schedule test alert ON: aviso de 10 min em ~{}s (evento TESTE às {})",
                lead,
                testEventStartsAt);

        taskScheduler.schedule(
                () -> {
                    log.info("Disparando alerta de teste TESTE via WebSocket (/topic/schedule)");
                    webSocketPublisher.publishScheduleAlert(new ScheduleTenMinuteAlertMessage(
                            "SCHEDULE_TEN_MINUTE",
                            "test-alert",
                            "TESTE",
                            10,
                            testEventStartsAt.toInstant().toString()));
                },
                Instant.now().plusSeconds(lead));
    }

    public Optional<ZonedDateTime> getTestEventStart() {
        return properties.isEnabled() && testEventStartsAt != null
                ? Optional.of(testEventStartsAt)
                : Optional.empty();
    }
}
