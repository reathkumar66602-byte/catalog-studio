package com.catalogstudio.supportticket.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
        UUID uuid,
        String ticketNumber,
        Long customerUserId,
        String customerEmail,
        String customerName,
        String subject,
        String description,
        String categoryCode,
        String categoryName,
        String priority,
        String status,
        String locale,
        Long assignedAdminUserId,
        String assignedAdminName,
        UUID chatSessionUuid,
        Instant slaDeadlineAt,
        boolean slaBreached,
        Instant firstResponseAt,
        Instant resolvedAt,
        Instant closedAt,
        String resolutionSummary,
        Instant createdAt,
        Instant updatedAt,
        List<TicketMessageResponse> messages,
        List<AttachmentResponse> attachments,
        List<TicketActivityResponse> activities
) {}
