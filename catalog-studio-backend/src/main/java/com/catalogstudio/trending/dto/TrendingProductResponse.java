package com.catalogstudio.trending.dto;

public record TrendingProductResponse(
        String externalId,
        String title,
        String brand,
        String priceLabel,
        String mrpLabel,
        String rating,
        String reviewCount,
        String imageUrl,
        String productUrl
) {}
