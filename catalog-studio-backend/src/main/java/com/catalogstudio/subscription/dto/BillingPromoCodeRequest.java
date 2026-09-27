package com.catalogstudio.subscription.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BillingPromoCodeRequest(
        @NotBlank @Size(max = 40) String code,
        @Size(max = 255) String description,
        @NotBlank @Size(max = 20) String discountType,
        @NotNull @DecimalMin("0") BigDecimal discountValue,
        Integer maxUses,
        LocalDate validFrom,
        LocalDate validUntil,
        @Size(max = 20) String status
) {}
