package com.catalogstudio.subscription.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BillingPromoCodeResponse(
        UUID id,
        String code,
        String description,
        String discountType,
        BigDecimal discountValue,
        Integer maxUses,
        int usedCount,
        LocalDate validFrom,
        LocalDate validUntil,
        String status,
        Instant createdAt
) {}
