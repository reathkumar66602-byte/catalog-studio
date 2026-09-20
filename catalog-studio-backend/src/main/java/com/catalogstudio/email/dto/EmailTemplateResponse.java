package com.catalogstudio.email.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EmailTemplateResponse(
        UUID id,
        String slug,
        String name,
        String subject,
        String htmlBody,
        String textBody,
        List<String> variables,
        boolean enabled,
        Instant updatedAt
) {}
