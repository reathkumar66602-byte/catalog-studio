package com.catalogstudio.trending.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlipkartSearchParser {

    private FlipkartSearchParser() {}

    public static List<TrendingHit> parse(JsonNode root) {
        Map<String, TrendingHit> unique = new LinkedHashMap<>();
        JsonNode slots = root.path("RESPONSE").path("slots");
        if (!slots.isArray()) {
            return List.of();
        }
        for (JsonNode slot : slots) {
            JsonNode products = slot.path("widget").path("data").path("products");
            if (!products.isArray()) {
                continue;
            }
            for (JsonNode product : products) {
                JsonNode value = product.path("productInfo").path("value");
                String id = text(value, "id");
                if (id == null) {
                    continue;
                }
                JsonNode titles = value.path("titles");
                String title = firstText(titles, "title", "newTitle");
                if (title == null) {
                    continue;
                }
                String url = absolute(text(value, "baseUrl"), text(value, "smartUrl"));
                if (url == null) {
                    continue;
                }
                unique.putIfAbsent(id, new TrendingHit(
                        TrendingText.clip(id, 128),
                        TrendingText.clip(title, 500),
                        TrendingText.clip(firstText(value, "productBrand"), 160),
                        price(value.path("pricing").path("finalPrice")),
                        price(value.path("pricing").path("mrp")),
                        rating(value.path("rating")),
                        reviews(value.path("rating")),
                        image(value.path("media")),
                        url
                ));
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static String image(JsonNode media) {
        JsonNode images = media.path("images");
        if (!images.isArray() || images.isEmpty()) {
            return null;
        }
        String url = text(images.get(0), "url");
        if (url == null) {
            return null;
        }
        return url.replace("{@width}", "400")
                .replace("{@height}", "400")
                .replace("{@quality}", "70");
    }

    private static String absolute(String baseUrl, String smartUrl) {
        String path = baseUrl != null ? baseUrl : smartUrl;
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "https://www.flipkart.com" + path;
    }

    private static String price(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path("value");
        if (value.isNumber()) {
            return TrendingText.rupee(value.asText());
        }
        if (node.isNumber()) {
            return TrendingText.rupee(node.asText());
        }
        return null;
    }

    private static String rating(JsonNode rating) {
        JsonNode average = rating.path("average");
        if (!average.isNumber()) {
            return null;
        }
        return TrendingText.clip(average.asText(), 32);
    }

    private static String reviews(JsonNode rating) {
        JsonNode reviewCount = rating.path("reviewCount");
        if (reviewCount.isNumber() && reviewCount.asInt() > 0) {
            return TrendingText.clip(reviewCount.asText(), 32);
        }
        JsonNode count = rating.path("count");
        if (count.isNumber() && count.asInt() > 0) {
            return TrendingText.clip(count.asText(), 32);
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String raw = value.asText();
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
