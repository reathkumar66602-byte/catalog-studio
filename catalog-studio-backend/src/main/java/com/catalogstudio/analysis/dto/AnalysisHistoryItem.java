package com.catalogstudio.analysis.dto;

import com.catalogstudio.analysis.entity.ProductAnalysis;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AnalysisHistoryItem(
        UUID id,
        UUID productId,
        String productName,
        String productImage,
        String provider,
        String model,
        String status,
        BigDecimal confidence,
        Instant createdAt
) {
    public static AnalysisHistoryItem from(ProductAnalysis analysis) {
        String name = analysis.getProduct() == null ? null : analysis.getProduct().getName();
        String image = null;
        if (analysis.getProduct() != null && analysis.getProduct().getImages() != null
                && !analysis.getProduct().getImages().isEmpty()) {
            image = analysis.getProduct().getImages().get(0).getImageUrl();
        }
        return new AnalysisHistoryItem(
                analysis.getUuid(),
                analysis.getProduct() == null ? null : analysis.getProduct().getUuid(),
                name,
                image,
                analysis.getProvider(),
                analysis.getModel(),
                analysis.getStatus().name(),
                analysis.getOverallConfidence(),
                analysis.getCreatedAt()
        );
    }
}
