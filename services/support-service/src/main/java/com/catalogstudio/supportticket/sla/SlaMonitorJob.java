package com.catalogstudio.supportticket.sla;

import com.catalogstudio.supportticket.entity.SupportSlaConfiguration;
import com.catalogstudio.supportticket.entity.SupportTicket;
import com.catalogstudio.supportticket.repository.SupportSlaConfigurationRepository;
import com.catalogstudio.supportticket.repository.SupportTicketRepository;
import com.catalogstudio.supportticket.service.SupportEmailNotifier;
import com.catalogstudio.supportticket.service.TicketActivityService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaMonitorJob {

    private final SupportTicketRepository ticketRepository;
    private final SupportSlaConfigurationRepository slaConfigurationRepository;
    private final SupportEmailNotifier emailNotifier;
    private final TicketActivityService activityService;

    @Scheduled(fixedDelayString = "${catalogstudio.support.sla.check-interval-ms:60000}")
    @Transactional
    public void checkSla() {
        SupportSlaConfiguration config = slaConfigurationRepository.findFirstByActiveTrueOrderByIdAsc().orElse(null);
        int warnHours = config == null ? 4 : Math.max(1, config.getWarnHoursBefore());
        Instant now = Instant.now();
        Instant warnBefore = now.plus(warnHours, ChronoUnit.HOURS);
        List<SupportTicket> tickets = ticketRepository.findTicketsNeedingSlaAttention(now, warnBefore);
        for (SupportTicket ticket : tickets) {
            if (ticket.getSlaDeadlineAt() == null) {
                continue;
            }
            if (ticket.getSlaDeadlineAt().isBefore(now) && ticket.getSlaBreachedAt() == null) {
                ticket.setSlaBreachedAt(now);
                activityService.log(ticket.getId(), null, "SYSTEM", "SLA_BREACHED",
                        null, ticket.getSlaDeadlineAt().toString(), null);
                emailNotifier.notify(ticket, "SLA_BREACH", "support-ticket-sla-breach", Map.of());
                log.info("SLA breached for ticket {}", ticket.getTicketNumber());
            } else if (ticket.getSlaWarnedAt() == null
                    && !ticket.getSlaDeadlineAt().isAfter(warnBefore)
                    && ticket.getSlaDeadlineAt().isAfter(now)) {
                ticket.setSlaWarnedAt(now);
                activityService.log(ticket.getId(), null, "SYSTEM", "SLA_WARN",
                        null, ticket.getSlaDeadlineAt().toString(), null);
                emailNotifier.notify(ticket, "SLA_WARN", "support-ticket-sla-warn", Map.of());
                log.info("SLA warn for ticket {}", ticket.getTicketNumber());
            }
        }
        if (!tickets.isEmpty()) {
            ticketRepository.saveAll(tickets);
        }
    }
}
