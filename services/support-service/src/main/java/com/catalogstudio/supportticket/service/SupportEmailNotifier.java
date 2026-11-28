package com.catalogstudio.supportticket.service;

import com.catalogstudio.events.support.SupportEmailNotificationEvent;
import com.catalogstudio.support.client.SupportEmailClient;
import com.catalogstudio.support.messaging.SupportNotificationPublisher;
import com.catalogstudio.supportticket.entity.SupportEmailLog;
import com.catalogstudio.supportticket.entity.SupportTicket;
import com.catalogstudio.supportticket.repository.SupportEmailLogRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SupportEmailNotifier {

    private final Optional<SupportNotificationPublisher> publisher;
    private final SupportEmailClient emailClient;
    private final SupportEmailLogRepository emailLogRepository;
    private final boolean messagingEnabled;

    @Autowired
    public SupportEmailNotifier(
            @Autowired(required = false) SupportNotificationPublisher publisher,
            SupportEmailClient emailClient,
            SupportEmailLogRepository emailLogRepository,
            @Value("${catalogstudio.messaging.enabled:true}") boolean messagingEnabled) {
        this.publisher = Optional.ofNullable(publisher);
        this.emailClient = emailClient;
        this.emailLogRepository = emailLogRepository;
        this.messagingEnabled = messagingEnabled;
    }

    @Transactional
    public void notify(SupportTicket ticket, String eventType, String templateSlug, Map<String, String> extra) {
        if (ticket.getCustomerEmail() == null || ticket.getCustomerEmail().isBlank()) {
            return;
        }
        String idempotencyKey = eventType + ":" + ticket.getTicketNumber() + ":" + UUID.randomUUID();
        if (emailLogRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }
        SupportEmailLog logRow = emailLogRepository.save(SupportEmailLog.builder()
                .ticketId(ticket.getId())
                .eventType(eventType)
                .recipient(ticket.getCustomerEmail())
                .templateSlug(templateSlug)
                .idempotencyKey(idempotencyKey)
                .status("QUEUED")
                .build());

        Map<String, String> vars = new HashMap<>();
        if (extra != null) {
            vars.putAll(extra);
        }
        vars.putIfAbsent("ticketNumber", ticket.getTicketNumber());
        vars.putIfAbsent("subject", ticket.getSubject());
        vars.putIfAbsent("status", ticket.getStatus().name());
        vars.putIfAbsent("priority", ticket.getPriority().name());

        SupportEmailNotificationEvent event = new SupportEmailNotificationEvent(
                eventType,
                ticket.getTicketNumber(),
                ticket.getCustomerEmail(),
                ticket.getCustomerName(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getStatus().name(),
                ticket.getPriority().name(),
                ticket.getAssignedAdminName(),
                ticket.getLocale(),
                idempotencyKey,
                vars);

        boolean delivered = false;
        String error = null;

        if (messagingEnabled && publisher.isPresent()) {
            try {
                delivered = publisher.get().publish(event);
            } catch (Exception ex) {
                log.warn("Rabbit publish failed for support email {}, falling back to HTTP: {}",
                        eventType, ex.toString());
                error = ex.toString();
            }
        }

        if (!delivered) {
            try {
                delivered = emailClient.send(event);
                if (!delivered && error == null) {
                    error = "HTTP email fallback returned false";
                }
            } catch (Exception ex) {
                log.warn("HTTP email fallback failed for {}: {}", eventType, ex.toString());
                error = ex.toString();
            }
        }

        logRow.setStatus(delivered ? "SENT" : "FAILED");
        if (!delivered) {
            logRow.setError(error);
        }
        emailLogRepository.save(logRow);
    }
}
