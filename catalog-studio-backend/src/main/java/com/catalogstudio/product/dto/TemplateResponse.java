package com.catalogstudio.product.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        String marketplace,
        String productType,
        Map<String, Object> attributes,
        Instant createdAt,
        Instant updatedAt
) {}
