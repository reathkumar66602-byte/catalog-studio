package com.catalogstudio.supportticket.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketMessageResponse(
        UUID uuid,
        Long authorUserId,
        String authorName,
        String authorRole,
        String body,
        boolean internal,
        Instant createdAt,
        List<AttachmentResponse> attachments
) {}
