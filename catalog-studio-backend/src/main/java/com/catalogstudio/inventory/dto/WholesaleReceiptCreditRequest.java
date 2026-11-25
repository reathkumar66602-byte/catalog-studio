package com.catalogstudio.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WholesaleReceiptCreditRequest(
        @NotNull Long buyerUserId,
        @NotNull UUID receiptId,
        List<@Valid Line> lines
) {
    public record Line(
            @NotNull UUID lineId,
            String sku,
            String title,
            String colorLabel,
            String sizeLabel,
            @Min(0) int acceptedQty,
            BigDecimal unitCost
    ) {}
}
