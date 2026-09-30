package com.catalogstudio.campaign.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationTemplateResponse(
        UUID id,
        String slug,
        String name,
        String channel,
        String campaignType,
        String subject,
        String bodyText,
        String bodyHtml,
        List<String> variables,
        boolean enabled,
        Instant updatedAt
) {}
