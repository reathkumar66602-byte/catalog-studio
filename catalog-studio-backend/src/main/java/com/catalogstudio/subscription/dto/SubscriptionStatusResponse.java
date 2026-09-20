package com.catalogstudio.subscription.dto;

import java.util.Map;

public record SubscriptionStatusResponse(
        String plan,
        String status,
        String effectiveStatus,
        String startDate,
        String endDate,
        boolean accessEntitled,
        boolean requiresRecharge,
        boolean trialActive,
        long daysRemaining,
        Map<String, Object> features,
        String registeredEmail,
        int trialDaysConfigured,
        String rechargeHeadline,
        String rechargeBody,
        String pendingPlan,
        PaymentNotice paymentNotice
) {
    public record PaymentNotice(
            String paymentProvider,
            String whatsappNumber,
            String upiId,
            String payeeName,
            String qrImageUrl,
            String instructions
    ) {}
}
