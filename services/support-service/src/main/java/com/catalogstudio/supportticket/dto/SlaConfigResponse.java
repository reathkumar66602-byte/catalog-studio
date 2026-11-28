package com.catalogstudio.supportticket.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record SlaConfigResponse(
        UUID uuid,
        String name,
        String timezone,
        String workingDays,
        LocalTime workStart,
        LocalTime workEnd,
        int initialResponseBusinessDays,
        int warnHoursBefore,
        boolean active,
        List<Holiday> holidays,
        Instant createdAt,
        Instant updatedAt
) {
    public record Holiday(LocalDate holidayDate, String name) {}
}
