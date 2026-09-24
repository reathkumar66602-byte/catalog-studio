package com.catalogstudio.trending.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MeeshoSearchParser {

    private MeeshoSearchParser() {}

    public static List<TrendingHit> parse(JsonNode root) {
        JsonNode catalogs = root.path("catalogs");
        if (!catalogs.isArray()) {
            return List.of();
        }
        Map<String, TrendingHit> unique = new LinkedHashMap<>();
        for (JsonNode catalog : catalogs) {
            String productId = text(catalog, "product_id");
            String name = text(catalog, "name");
            if (productId == null || name == null) {
                continue;
            }
            String slug = text(catalog, "slug");
            String path = slug == null ? productId : slug + "/p/" + productId;
            JsonNode summary = catalog.path("catalog_reviews_summary");
            unique.putIfAbsent(productId, new TrendingHit(
                    TrendingText.clip(productId, 128),
                    TrendingText.clip(name, 500),
                    TrendingText.clip(text(catalog, "sub_sub_category_name"), 160),
                    price(catalog.path("min_product_price")),
                    price(catalog.path("original_price")),
                    rating(summary.path("average_rating")),
                    reviews(summary.path("review_count")),
                    text(catalog, "image"),
                    "https://www.meesho.com/" + path
            ));
        }
        return new ArrayList<>(unique.values());
    }

    public static String cursor(JsonNode root) {
        JsonNode cursor = root.path("cursor");
        if (cursor.isMissingNode() || cursor.isNull()) {
            return null;
        }
        String value = cursor.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static String price(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        if (node.asInt() <= 0) {
            return null;
        }
        return TrendingText.rupee(node.asText());
    }

    private static String rating(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return TrendingText.clip(node.asText(), 32);
    }

    private static String reviews(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isNumber() || node.asInt() <= 0) {
            return null;
        }
        return TrendingText.clip(node.asText(), 32);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String raw = value.asText();
        return raw == null || raw.isBlank() ? null : raw.trim();
    }
}
