package com.catalogstudio.trending.service;

public record TrendingHit(
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
