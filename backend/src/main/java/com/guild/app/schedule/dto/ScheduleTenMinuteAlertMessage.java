package com.guild.app.schedule.dto;

public record ScheduleTenMinuteAlertMessage(
        String type,
        String eventId,
        String label,
        int minutesRemaining,
        String startsAt) {}
