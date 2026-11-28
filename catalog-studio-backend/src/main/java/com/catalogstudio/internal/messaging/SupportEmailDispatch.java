package com.catalogstudio.internal.messaging;

import com.catalogstudio.email.service.TemplatedEmailService;
import com.catalogstudio.events.support.SupportEmailNotificationEvent;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupportEmailDispatch {

    private final TemplatedEmailService templatedEmailService;

    public boolean dispatch(SupportEmailNotificationEvent event) {
        if (event == null || !StringUtils.hasText(event.recipientEmail())) {
            return false;
        }
        String slug = templateSlug(event.eventType());
        Map<String, String> vars = new HashMap<>();
        if (event.extra() != null) {
            vars.putAll(event.extra());
        }
        vars.putIfAbsent("name", StringUtils.hasText(event.recipientName()) ? event.recipientName() : "there");
        vars.putIfAbsent("ticketNumber", nullToEmpty(event.ticketNumber()));
        vars.putIfAbsent("subject", nullToEmpty(event.subject()));
        vars.putIfAbsent("description", nullToEmpty(event.description()));
        vars.putIfAbsent("status", nullToEmpty(event.status()));
        vars.putIfAbsent("priority", nullToEmpty(event.priority()));
        vars.putIfAbsent("assignedTo", nullToEmpty(event.assignedTo()));
        vars.putIfAbsent("locale", nullToEmpty(event.locale()));
        vars.putIfAbsent("eventType", nullToEmpty(event.eventType()));
        try {
            boolean sent = templatedEmailService.send(slug, event.recipientEmail().trim(), vars);
            if (!sent) {
                log.warn("Support email not sent for ticket {} type {}", event.ticketNumber(), event.eventType());
            }
            return sent;
        } catch (Exception ex) {
            log.warn("Failed support email for ticket {}: {}", event.ticketNumber(), ex.toString());
            return false;
        }
    }

    static String templateSlug(String eventType) {
        if (eventType == null) {
            return "support-ticket-update";
        }
        return switch (eventType.trim().toUpperCase()) {
            case "TICKET_CREATED" -> "support-ticket-created";
            case "TICKET_ASSIGNED" -> "support-ticket-assigned";
            case "ADMIN_REPLIED" -> "support-ticket-admin-reply";
            case "CUSTOMER_REPLIED" -> "support-ticket-customer-reply";
            case "STATUS_CHANGED" -> "support-ticket-status-changed";
            case "TICKET_RESOLVED" -> "support-ticket-resolved";
            case "TICKET_CLOSED" -> "support-ticket-closed";
            case "TICKET_REOPENED" -> "support-ticket-reopened";
            case "SLA_WARN" -> "support-ticket-sla-warn";
            case "SLA_BREACH" -> "support-ticket-sla-breach";
            default -> "support-ticket-update";
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
