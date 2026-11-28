package com.catalogstudio.supportticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record SlaConfigRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Size(max = 64) String workingDays,
        @NotNull LocalTime workStart,
        @NotNull LocalTime workEnd,
        @NotNull Integer initialResponseBusinessDays,
        @NotNull Integer warnHoursBefore,
        Boolean active,
        List<Holiday> holidays
) {
    public record Holiday(@NotNull LocalDate holidayDate, @NotBlank @Size(max = 160) String name) {}
}
