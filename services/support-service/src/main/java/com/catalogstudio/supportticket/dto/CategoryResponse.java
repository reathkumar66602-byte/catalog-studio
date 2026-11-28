package com.catalogstudio.supportticket.dto;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID uuid,
        String code,
        String name,
        String description,
        boolean active,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {}
