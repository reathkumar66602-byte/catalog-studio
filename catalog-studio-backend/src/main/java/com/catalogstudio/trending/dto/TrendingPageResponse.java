package com.catalogstudio.trending.dto;

import java.time.Instant;
import java.util.List;

public record TrendingPageResponse(
        String marketplace,
        String category,
        String categoryLabel,
        int page,
        int pageSize,
        boolean cached,
        boolean hasMore,
        Instant seenAt,
        List<TrendingProductResponse> products
) {}
