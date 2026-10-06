package com.catalogstudio.campaign.dto;

import java.time.Instant;
import java.util.UUID;

public record CampaignRunResponse(
        UUID id,
        String campaignType,
        String channel,
        String promoCode,
        Integer maxPriorSends,
        String status,
        int totalRecipients,
        int sentCount,
        int skippedCount,
        int failedCount,
        String notes,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String whatsappCapability
) {}
