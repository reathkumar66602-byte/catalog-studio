package com.catalogstudio.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.catalogstudio.ai.AIProductAnalysisService;
import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisResponse;
import com.catalogstudio.common.exception.ApiException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnalysisJsonParser {

    private final ObjectMapper objectMapper;

    public AnalysisJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProductAnalysisResponse parse(String rawJson, String provider, String model) {
        try {
            String json = extractJson(rawJson);
            JsonNode node = objectMapper.readTree(json);
            return new ProductAnalysisResponse(
                    text(node, "productType"),
                    text(node, "category"),
                    text(node, "subCategory"),
                    text(node, "gender"),
                    text(node, "ageGroup"),
                    text(node, "primaryColor"),
                    stringList(node, "secondaryColors"),
                    number(node, "colorConfidence"),
                    text(node, "pattern"),
                    number(node, "patternConfidence"),
                    text(node, "material"),
                    number(node, "materialConfidence"),
                    text(node, "sleeveType"),
                    text(node, "neckType"),
                    text(node, "collarType"),
                    text(node, "fit"),
                    text(node, "occasion"),
                    text(node, "style"),
                    text(node, "productDescription"),
                    stringList(node, "suggestedTitles"),
                    stringList(node, "suggestedDescriptions"),
                    stringList(node, "keywords"),
                    number(node, "overallConfidence"),
                    stringList(node, "uncertainFields"),
                    provider,
                    model
            );
        } catch (Exception ex) {
            throw ApiException.badRequest("AI returned invalid JSON");
        }
    }

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        return trimmed;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private Double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asDouble();
    }

    private List<String> stringList(JsonNode node, String field) {
        JsonNode value = node.get(field);
        List<String> result = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(item -> {
                if (!item.isNull() && !item.asText().isBlank()) {
                    result.add(item.asText());
                }
            });
        }
        return result;
    }
}
