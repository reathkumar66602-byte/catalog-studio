package com.catalogstudio.wholesale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Walk-in / offline bill created by the supplier (no platform buyer account). */
public record ManualBillRequest(
        @NotBlank @Size(max = 160) String buyerName,
        @NotBlank @Size(max = 20) String buyerMobile,
        @Email @Size(max = 255) String buyerEmail,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal gstPercent,
        @Size(max = 1000) String note,
        @NotEmpty @Size(max = 100) @Valid List<Item> items
) {
    public record Item(
            @NotNull UUID variantId,
            @Min(1) @Max(100000) int quantity,
            /** When null, the variant's list price is used. */
            @DecimalMin("0.00") BigDecimal unitPrice
    ) {}
}
