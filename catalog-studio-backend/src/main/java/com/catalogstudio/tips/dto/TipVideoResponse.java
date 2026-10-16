package com.catalogstudio.tips.dto;

import java.time.Instant;
import java.util.UUID;

public record TipVideoResponse(
        UUID id,
        String title,
        String publicUrl,
        String contentType,
        long sizeBytes,
        String uploadedByEmail,
        Instant createdAt
) {}
