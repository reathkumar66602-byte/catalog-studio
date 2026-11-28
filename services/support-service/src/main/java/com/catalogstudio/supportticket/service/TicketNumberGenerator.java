package com.catalogstudio.supportticket.service;

import com.catalogstudio.supportticket.repository.SupportTicketRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketNumberGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SupportTicketRepository ticketRepository;

    public String next() {
        String day = LocalDate.now(ZoneOffset.UTC).format(DAY);
        for (int attempt = 0; attempt < 32; attempt++) {
            String candidate = "CS-" + day + "-" + String.format("%05d", RANDOM.nextInt(100_000));
            if (!ticketRepository.existsByTicketNumber(candidate)) {
                return candidate;
            }
        }
        return "CS-" + day + "-" + String.format("%05d", Math.floorMod(System.nanoTime(), 100_000));
    }
}
