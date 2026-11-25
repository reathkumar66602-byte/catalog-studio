package com.catalogstudio.wholesale.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        String status,
        String note,
        String lastError,
        Instant confirmedAt,
        List<Line> lines
) {
    public record Line(
            UUID id,
            UUID orderItemId,
            String sku,
            String productTitle,
            int orderedQty,
            int receivedQty,
            int missingQty,
            int damagedQty,
            int rejectedQty,
            int acceptedQty
    ) {}
}
