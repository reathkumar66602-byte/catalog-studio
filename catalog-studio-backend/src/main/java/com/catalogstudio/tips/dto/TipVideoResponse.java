package com.catalogstudio.tips.dto;

import java.time.Instant;
import java.util.UUID;

public record TipVideoResponse(
        UUID id,
        String title,
        String kind,
        String language,
        UUID seriesKey,
        String publicUrl,
        String downloadUrl,
        String contentType,
        long sizeBytes,
        String uploadedByEmail,
        Instant createdAt
) {}
