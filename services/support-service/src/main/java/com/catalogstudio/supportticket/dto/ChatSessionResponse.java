package com.catalogstudio.supportticket.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(
        UUID uuid,
        String locale,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
