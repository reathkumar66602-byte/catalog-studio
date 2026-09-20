package com.catalogstudio.subscription.dto;

public record BillingSettingsResponse(
        int trialDays,
        String trialPlan,
        String whatsappNumber,
        String whatsappMessageTemplate,
        String upiId,
        String payeeName,
        String qrImageUrl,
        String paymentProvider,
        String paymentInstructions,
        String rechargeHeadline,
        String rechargeBody
) {}
