package com.catalogstudio.supportticket.sla;

import com.catalogstudio.supportticket.entity.SupportSlaConfiguration;
import com.catalogstudio.supportticket.entity.SupportSlaHoliday;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SlaCalculator {

    public Instant computeDeadline(
            Instant from,
            SupportSlaConfiguration config,
            List<SupportSlaHoliday> holidays) {
        if (config == null) {
            return from.plusSeconds(24 * 3600L);
        }
        ZoneId zone = ZoneId.of(config.getTimezone() == null ? "Asia/Kolkata" : config.getTimezone());
        Set<LocalDate> holidayDates = holidays == null ? Set.of()
                : holidays.stream().map(SupportSlaHoliday::getHolidayDate).collect(Collectors.toSet());
        Set<DayOfWeek> workingDays = parseWorkingDays(config.getWorkingDays());
        LocalTime start = config.getWorkStart() == null ? LocalTime.of(9, 0) : config.getWorkStart();
        LocalTime end = config.getWorkEnd() == null ? LocalTime.of(18, 0) : config.getWorkEnd();
        int businessDays = Math.max(1, config.getInitialResponseBusinessDays());

        LocalDateTime cursor = LocalDateTime.ofInstant(from, zone);
        if (!isWorkingMoment(cursor, workingDays, holidayDates, start, end)) {
            cursor = nextWorkStart(cursor, workingDays, holidayDates, start, end);
        }
        int remaining = businessDays;
        while (remaining > 0) {
            cursor = cursor.toLocalDate().atTime(end);
            remaining--;
            if (remaining > 0) {
                cursor = nextWorkStart(cursor.plusMinutes(1), workingDays, holidayDates, start, end);
            }
        }
        return cursor.atZone(zone).toInstant();
    }

    private boolean isWorkingMoment(
            LocalDateTime dt,
            Set<DayOfWeek> workingDays,
            Set<LocalDate> holidays,
            LocalTime start,
            LocalTime end) {
        LocalDate date = dt.toLocalDate();
        if (holidays.contains(date) || !workingDays.contains(dt.getDayOfWeek())) {
            return false;
        }
        LocalTime time = dt.toLocalTime();
        return !time.isBefore(start) && time.isBefore(end);
    }

    private LocalDateTime nextWorkStart(
            LocalDateTime from,
            Set<DayOfWeek> workingDays,
            Set<LocalDate> holidays,
            LocalTime start,
            LocalTime end) {
        LocalDateTime cursor = from;
        for (int i = 0; i < 370; i++) {
            LocalDate date = cursor.toLocalDate();
            if (!holidays.contains(date) && workingDays.contains(cursor.getDayOfWeek())) {
                LocalDateTime candidate = date.atTime(start);
                if (!candidate.isBefore(from) && start.isBefore(end)) {
                    return candidate;
                }
            }
            cursor = date.plusDays(1).atStartOfDay();
        }
        return from.plusDays(1);
    }

    private Set<DayOfWeek> parseWorkingDays(String raw) {
        if (raw == null || raw.isBlank() || "MON-FRI".equalsIgnoreCase(raw.trim())) {
            return EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String part : raw.split("[,|]")) {
            String p = part.trim().toUpperCase();
            if (p.contains("-")) {
                String[] range = p.split("-", 2);
                DayOfWeek a = day(range[0]);
                DayOfWeek b = day(range[1]);
                DayOfWeek cur = a;
                while (true) {
                    days.add(cur);
                    if (cur == b) break;
                    cur = cur.plus(1);
                }
            } else if (!p.isBlank()) {
                days.add(day(p));
            }
        }
        return days.isEmpty()
                ? EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                : days;
    }

    private DayOfWeek day(String token) {
        return switch (token.trim().toUpperCase()) {
            case "MON", "MONDAY" -> DayOfWeek.MONDAY;
            case "TUE", "TUESDAY" -> DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
            case "THU", "THURSDAY" -> DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY" -> DayOfWeek.FRIDAY;
            case "SAT", "SATURDAY" -> DayOfWeek.SATURDAY;
            case "SUN", "SUNDAY" -> DayOfWeek.SUNDAY;
            default -> DayOfWeek.MONDAY;
        };
    }
}
