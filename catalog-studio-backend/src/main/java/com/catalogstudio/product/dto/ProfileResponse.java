package com.catalogstudio.product.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String name,
        String marketplace,
        Map<String, Object> profileJson,
        Instant createdAt,
        Instant updatedAt
) {}
