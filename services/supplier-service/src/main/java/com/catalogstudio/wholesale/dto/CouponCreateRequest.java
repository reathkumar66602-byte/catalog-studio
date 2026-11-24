package com.catalogstudio.wholesale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record CouponCreateRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Pattern(regexp = "(?i)PERCENT|FIXED", message = "Discount type must be PERCENT or FIXED")
        String discountType,
        @NotNull @DecimalMin(value = "0.01", message = "Discount value must be greater than 0")
        BigDecimal discountValue,
        @DecimalMin("0.00") BigDecimal minOrder,
        @DecimalMin("0.00") BigDecimal maxDiscount,
        Instant startsAt,
        Instant endsAt,
        @Min(1) Integer usageLimit,
        Boolean active
) {}
