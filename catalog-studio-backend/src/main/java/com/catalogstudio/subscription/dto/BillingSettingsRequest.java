package com.catalogstudio.subscription.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record BillingSettingsRequest(
        @Min(0) Integer trialDays,
        @Size(max = 40) String trialPlan,
        @Size(max = 40) String whatsappNumber,
        String whatsappMessageTemplate,
        @Size(max = 120) String upiId,
        @Size(max = 160) String payeeName,
        @Size(max = 500) String qrImageUrl,
        @Size(max = 40) String paymentProvider,
        String paymentInstructions,
        @Size(max = 200) String rechargeHeadline,
        String rechargeBody
) {}
