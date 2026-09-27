package com.catalogstudio.subscription.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

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
        String rechargeBody,
        @Size(max = 160) String companyLegalName,
        @Size(max = 20) String companyGstin,
        @Size(max = 120) String parentCompanyName,
        @DecimalMin("0") BigDecimal serviceChargePercent,
        @DecimalMin("0") BigDecimal gstPercent
) {}
