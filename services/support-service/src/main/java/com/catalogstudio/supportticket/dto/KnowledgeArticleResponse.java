package com.catalogstudio.supportticket.dto;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeArticleResponse(
        UUID uuid,
        String slug,
        String title,
        String summary,
        String body,
        String locale,
        String category,
        String tags,
        boolean active,
        boolean verified,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {}
