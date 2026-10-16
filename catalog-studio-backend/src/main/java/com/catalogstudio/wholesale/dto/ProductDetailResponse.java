package com.catalogstudio.wholesale.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductDetailResponse(
        UUID id,
        String slug,
        String title,
        String code,
        String status,
        String description,
        String details,
        BigDecimal mrp,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        int totalStock,
        int bookedCount,
        CategoryResponse category,
        StoreSummary store,
        List<ProductImageResponse> images,
        List<ColorSwatch> colors,
        List<String> sizes,
        /** colour label -> size label -> available pieces */
        Map<String, Map<String, Integer>> stockMatrix,
        List<VariantCell> variants
) {
    public record StoreSummary(UUID id, String name, String slug, String city, String state) {}
}