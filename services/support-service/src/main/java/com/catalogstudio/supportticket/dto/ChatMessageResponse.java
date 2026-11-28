package com.catalogstudio.supportticket.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID uuid,
        String role,
        String content,
        boolean suggestTicket,
        Instant createdAt
) {}
