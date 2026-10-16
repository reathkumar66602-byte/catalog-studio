package com.catalogstudio.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VariantResponse(
        UUID id,
        UUID productId,
        String productName,
        String sku,
        String sizeLabel,
        String colorLabel,
        String barcode,
        BigDecimal price,
        String status,
        int onHand,
        int reserved,
        int available,
        Instant updatedAt
) {}
