package com.catalogstudio.wholesale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** All fields optional. Per-line prices win over acceptedTotal when both are sent. */
public record AcceptOrderRequest(
        @DecimalMin("0.00") BigDecimal acceptedTotal,
        @Valid List<Line> lines,
        @Size(max = 1000) String supplierNote
) {
    public record Line(@NotNull UUID itemId, @NotNull @DecimalMin("0.00") BigDecimal unitPrice) {}
}