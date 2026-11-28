package com.catalogstudio.supportticket.dto;

import java.time.Instant;
import java.util.UUID;

public record TicketSummaryResponse(
        UUID uuid,
        String ticketNumber,
        String subject,
        String categoryCode,
        String priority,
        String status,
        String customerName,
        String customerEmail,
        Long assignedAdminUserId,
        String assignedAdminName,
        Instant slaDeadlineAt,
        boolean slaBreached,
        Instant createdAt,
        Instant updatedAt
) {}
