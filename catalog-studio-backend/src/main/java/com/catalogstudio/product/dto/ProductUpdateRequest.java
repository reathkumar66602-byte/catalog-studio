package com.catalogstudio.product.dto;

import java.util.List;
import java.util.UUID;

public record ProductUpdateRequest(
        String name,
        String productType,
        String category,
        String subcategory,
        String gender,
        String ageGroup,
        String primaryColor,
        List<String> secondaryColors,
        String pattern,
        String material,
        String sleeveType,
        String neckType,
        String collarType,
        String fit,
        String occasion,
        String style,
        String description,
        String status,
        UUID selectedTitleId,
        List<String> titles
) {}
