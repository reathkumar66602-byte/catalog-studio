package com.catalogstudio.analysis.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisResultResponse(
        UUID analysisId,
        UUID productId,
        String status,
        String provider,
        String model,
        BigDecimal overallConfidence,
        Instant createdAt,
        ProductPayload product
) {
    public record ProductPayload(
            String productType,
            String category,
            String subCategory,
            String gender,
            String ageGroup,
            String primaryColor,
            List<String> secondaryColors,
            Double colorConfidence,
            String pattern,
            Double patternConfidence,
            String material,
            Double materialConfidence,
            String sleeveType,
            String neckType,
            String collarType,
            String fit,
            String occasion,
            String style,
            String productDescription,
            List<String> suggestedTitles,
            List<String> suggestedDescriptions,
            List<String> keywords,
            Double overallConfidence,
            List<String> uncertainFields,
            List<ImagePayload> images
    ) {}

    public record ImagePayload(UUID id, String url, int order, boolean primary) {}
}
