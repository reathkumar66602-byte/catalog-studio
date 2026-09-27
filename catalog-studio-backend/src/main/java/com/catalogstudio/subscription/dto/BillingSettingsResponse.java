package com.catalogstudio.subscription.dto;

import java.math.BigDecimal;

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
        String rechargeBody,
        String companyLegalName,
        String companyGstin,
        String parentCompanyName,
        BigDecimal serviceChargePercent,
        BigDecimal gstPercent
) {}
