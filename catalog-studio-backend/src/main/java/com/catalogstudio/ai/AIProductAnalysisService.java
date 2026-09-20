package com.catalogstudio.ai;

import java.util.List;

public interface AIProductAnalysisService {

    ProductAnalysisResponse analyze(ProductAnalysisRequest request);

    record ProductAnalysisRequest(
            List<ImagePayload> images,
            String marketplace,
            String categoryHint,
            String productTypeHint
    ) {}

    record ImagePayload(byte[] bytes, String contentType, String filename) {}

    record ProductAnalysisResponse(
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
            String provider,
            String model
    ) {}
}
