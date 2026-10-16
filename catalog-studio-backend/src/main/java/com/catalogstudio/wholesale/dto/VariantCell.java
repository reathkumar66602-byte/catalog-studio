package com.catalogstudio.wholesale.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** onHand / reserved are only populated for the owning supplier. */
public record VariantCell(
        UUID id,
        String sku,
        String colorLabel,
        String sizeLabel,
        String colorHex,
        BigDecimal price,
        BigDecimal mrp,
        int available,
        Integer onHand,
        Integer reserved,
        String status
) {}