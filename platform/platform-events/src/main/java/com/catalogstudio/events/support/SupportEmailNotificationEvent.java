package com.catalogstudio.events.support;

import java.util.Map;

public record SupportEmailNotificationEvent(
        String eventType,
        String ticketNumber,
        String recipientEmail,
        String recipientName,
        String subject,
        String description,
        String status,
        String priority,
        String assignedTo,
        String locale,
        String idempotencyKey,
        Map<String, String> extra
) {}
