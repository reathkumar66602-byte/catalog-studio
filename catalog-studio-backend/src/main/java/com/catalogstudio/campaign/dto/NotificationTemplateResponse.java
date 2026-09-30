package com.catalogstudio.campaign.dto;

import java.util.UUID;

public record NotificationTemplateResponse(
        UUID id,
        String slug,
        String name,
        String channel,
        String campaignType,
        String subject,
        String bodyText,
        boolean enabled
) {}
