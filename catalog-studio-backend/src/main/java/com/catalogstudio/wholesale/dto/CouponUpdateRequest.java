package com.catalogstudio.wholesale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** All fields optional; only sent fields are updated. */
public record CouponUpdateRequest(
        @Size(max = 40) String code,
        @Pattern(regexp = "(?i)PERCENT|FIXED", message = "Discount type must be PERCENT or FIXED")
        String discountType,
        @DecimalMin(value = "0.01", message = "Discount value must be greater than 0")
        BigDecimal discountValue,
        @DecimalMin("0.00") BigDecimal minOrder,
        @DecimalMin("0.00") BigDecimal maxDiscount,
        Instant startsAt,
        Instant endsAt,
        @Min(1) Integer usageLimit,
        Boolean active
) {}
