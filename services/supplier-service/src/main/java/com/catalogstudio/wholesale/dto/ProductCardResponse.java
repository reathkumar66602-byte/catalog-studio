package com.catalogstudio.wholesale.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductCardResponse(
        UUID id,
        String slug,
        String title,
        String code,
        String status,
        String primaryImageUrl,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal mrp,
        List<ColorSwatch> colors,
        int totalStock,
        int bookedCount,
        String categoryName,
        String categorySlug,
        UUID storeId,
        String storeName,
        String storeSlug
) {}