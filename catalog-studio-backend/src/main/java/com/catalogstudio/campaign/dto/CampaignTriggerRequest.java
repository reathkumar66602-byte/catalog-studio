package com.catalogstudio.campaign.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CampaignTriggerRequest(
        @NotBlank String campaignType,
        @NotBlank String channel,
        String promoCode,
        String notes,
        /**
         * Only queue users who already received this campaign type at most this many times (SENT).
         * null = no filter (all matching audience). 0 = never sent before. 1 = 0 or 1 prior send, etc.
         */
        @Min(0) @Max(50) Integer maxPriorSends
) {}
