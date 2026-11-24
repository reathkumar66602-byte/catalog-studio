package com.catalogstudio.supplier.client;

import java.util.List;

public interface AiAutofillClient {

    record ImagePayload(byte[] bytes, String contentType, String filename) {}

    /** Mirrors platform AI analysis fields used by wholesale autofill. */
    record AutofillAnalysis(
            String category,
            String subCategory,
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
            String lengthMeters,
            String productType,
            String productDescription,
            List<String> suggestedTitles,
            List<String> suggestedDescriptions,
            List<String> keywords,
            Double overallConfidence
    ) {}

    AutofillAnalysis analyze(List<ImagePayload> images);
}
