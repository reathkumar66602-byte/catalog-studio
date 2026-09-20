package com.catalogstudio.referral.dto;

import java.math.BigDecimal;

public record ReferralLookupResponse(
        String code,
        boolean valid,
        String discountType,
        BigDecimal discountValue,
        int trialDays,
        String description
) {}
