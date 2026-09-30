package com.catalogstudio.campaign.dto;

import jakarta.validation.constraints.NotBlank;

public record CampaignTriggerRequest(
        @NotBlank String campaignType,
        @NotBlank String channel,
        String promoCode,
        String notes
) {}
