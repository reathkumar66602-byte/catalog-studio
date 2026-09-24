package com.catalogstudio.trending.service;

import com.catalogstudio.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeeshoTrendingClient implements TrendingMarketplaceClient {

    private static final String CATALOG = "https://engine1.tingily.com/api/products/c/%s/best-sellers?limit=%d&page=%d";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper objectMapper;

    @Override
    public String marketplace() {
        return "MEESHO";
    }

    @Override
    public TrendingBatch nextBatch(TrendingCatalog.Category category, String cursor, int nextPage) {
        int page = Math.max(nextPage, 1);
        String slug = catalogSlug(category.key());
        String url = CATALOG.formatted(slug, TrendingCatalog.PAGE_SIZE, page);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Meesho catalog HTTP {} category={}", response.statusCode(), category.key());
                throw ApiException.unavailable("Meesho listings are not available for this category right now.");
            }
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            List<TrendingHit> hits = parse(root);
            boolean hasNext = root.path("meta").path("hasNext").asBoolean(false);
            log.info("Meesho catalog category={} slug={} products={}", category.key(), slug, hits.size());
            return new TrendingBatch(hits, null, hasNext ? page + 1 : page);
        } catch (ApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("Meesho listings are not available for this category right now.");
        } catch (Exception ex) {
            log.warn("Meesho catalog failed category={} {}", category.key(), ex.getClass().getSimpleName());
            throw ApiException.unavailable("Meesho listings are not available for this category right now.");
        }
    }

    static String catalogSlug(String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank() || "all".equals(categoryKey)) {
            return "popular";
        }
        int split = categoryKey.lastIndexOf("--");
        if (split >= 0 && split + 2 < categoryKey.length()) {
            return categoryKey.substring(split + 2);
        }
        return categoryKey;
    }

    static List<TrendingHit> parse(JsonNode root) {
        List<TrendingHit> hits = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            TrendingHit hit = hit(item);
            if (hit != null) {
                hits.add(hit);
            }
            if (hits.size() == TrendingCatalog.PAGE_SIZE) {
                break;
            }
        }
        return hits;
    }

    private static TrendingHit hit(JsonNode item) {
        String link = text(item, "link");
        if (link == null || !link.contains("meesho.com")) {
            return null;
        }
        String title = text(item, "title");
        if (title == null) {
            return null;
        }
        String id = text(item, "id");
        if (id == null) {
            id = text(item, "hero_pid");
        }
        if (id == null) {
            return null;
        }
        String image = text(item, "image");
        if (image != null && !image.startsWith("https://")) {
            image = null;
        }
        return new TrendingHit(
                TrendingText.clip(id, 128),
                TrendingText.clip(title, 500),
                text(item, "brand"),
                TrendingText.rupee(text(item, "price")),
                null,
                rating(item.path("avg_rating")),
                reviews(item),
                image,
                link.startsWith("http") ? link : "https://www.meesho.com" + link);
    }

    private static String reviews(JsonNode item) {
        String ratings = text(item, "total_ratings");
        String reviews = text(item, "review_count");
        if (ratings != null && reviews != null) {
            return TrendingText.clip(ratings + " Ratings, " + reviews + " Reviews", 64);
        }
        if (reviews != null) {
            return TrendingText.clip(reviews + " Reviews", 64);
        }
        return ratings == null ? null : TrendingText.clip(ratings + " Ratings", 64);
    }

    private static String rating(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return String.format(Locale.ROOT, "%.1f", node.asDouble());
    }

    private static String text(JsonNode item, String field) {
        JsonNode node = item.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }
}
