package com.catalogstudio.wholesale.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minOrder,
        BigDecimal maxDiscount,
        Instant startsAt,
        Instant endsAt,
        Integer usageLimit,
        int usedCount,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
