package com.catalogstudio.supportticket.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        UUID uuid,
        String originalName,
        String contentType,
        long sizeBytes,
        Instant createdAt
) {}
