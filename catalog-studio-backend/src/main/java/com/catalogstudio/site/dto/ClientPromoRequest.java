package com.catalogstudio.site.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ClientPromoRequest(
        UUID clientId,
        @NotBlank @Size(max = 40) String code,
        @Size(max = 160) String headline,
        @Size(max = 255) String description,
        @NotBlank String discountType,
        @NotNull @DecimalMin("0") BigDecimal discountValue,
        int trialDays,
        LocalDate validFrom,
        LocalDate validUntil,
        @Size(max = 32) String status
) {}
