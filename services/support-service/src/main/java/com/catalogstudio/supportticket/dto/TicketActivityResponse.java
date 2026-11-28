package com.catalogstudio.supportticket.dto;

import java.time.Instant;

public record TicketActivityResponse(
        String action,
        String actorName,
        String fromValue,
        String toValue,
        Instant createdAt,
        String metadata
) {}
