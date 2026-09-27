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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeeshoTrendingClient implements TrendingMarketplaceClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper objectMapper;
    private final TingilyAuthService tingilyAuthService;
    private final OpenAiMeeshoLookup openAiMeeshoLookup;

    @Override
    public String marketplace() {
        return "MEESHO";
    }

    @Override
    public TrendingBatch nextBatch(TrendingCatalog.Category category, String cursor, int nextPage) {
        int page = Math.max(nextPage, 1);
        String slug = catalogSlug(category.key());
        int limit = tingilyAuthService.fetchLimit();
        List<TrendingHit> collected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int fetchPage = page;
        int guard = 0;
        boolean hasNext = false;
        Exception lastError = null;
        boolean searchFirst = CategoryRelevance.requiresPositiveDepartmentMatch(category.key())
                && pollutedSharedLeaf(CategoryRelevance.leafSlug(category.key()));

        if (searchFirst) {
            addFiltered(category, searchFallback(category, page), collected, seen);
            log.info("Meesho search-first category={} query={} kept={}",
                    category.key(), category.meeshoQuery(), collected.size());
        }

        while (!searchFirst && collected.size() < TrendingCatalog.PAGE_SIZE && guard++ < 4) {
            try {
                JsonNode root = fetchCatalog(slug, limit, fetchPage);
                List<TrendingHit> raw = parse(root, limit);
                hasNext = root.path("meta").path("hasNext").asBoolean(false)
                        || (root.path("data").isArray() && root.path("data").size() >= limit);
                addFiltered(category, raw, collected, seen);
                log.info("Meesho catalog category={} slug={} page={} raw={} kept={}",
                        category.key(), slug, fetchPage, raw.size(), collected.size());
                if (collected.size() >= TrendingCatalog.PAGE_SIZE || !hasNext) {
                    break;
                }
                fetchPage++;
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                lastError = ex;
                log.warn("Meesho catalog failed category={} page={} {}",
                        category.key(), fetchPage, ex.getClass().getSimpleName());
                break;
            }
        }

        if (searchFirst && collected.size() < TrendingCatalog.PAGE_SIZE) {
            try {
                JsonNode root = fetchCatalog(slug, limit, page);
                addFiltered(category, parse(root, limit), collected, seen);
            } catch (Exception ex) {
                log.warn("Meesho catalog supplement failed category={} {}",
                        category.key(), ex.getClass().getSimpleName());
            }
        }

        if (shouldFallbackToSearch(category, collected)) {
            addFiltered(category, searchFallback(category, page), collected, seen);
            log.info("Meesho search fallback category={} query={} kept={}",
                    category.key(), category.meeshoQuery(), collected.size());
        }

        if (collected.isEmpty() && lastError != null && !CategoryRelevance.isDepartmentChild(category.key())) {
            throw ApiException.unavailable("Meesho listings are not available for this category right now.");
        }

        List<TrendingHit> pageHits = collected.size() <= TrendingCatalog.PAGE_SIZE
                ? collected
                : collected.subList(0, TrendingCatalog.PAGE_SIZE);
        return new TrendingBatch(pageHits, null, hasNext || pageHits.size() == TrendingCatalog.PAGE_SIZE
                ? Math.max(fetchPage, page + 1)
                : page);
    }

    private static boolean pollutedSharedLeaf(String leaf) {
        return Set.of("shirts", "jackets", "sweatshirts", "sweaters", "kurta-sets", "t-shirts").contains(leaf);
    }

    private static void addFiltered(
            TrendingCatalog.Category category,
            List<TrendingHit> incoming,
            List<TrendingHit> collected,
            Set<String> seen
    ) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        for (TrendingHit hit : incoming) {
            if (!CategoryRelevance.matches(category.key(), hit.title())) {
                continue;
            }
            String key = productKey(hit.productUrl(), hit.externalId());
            if (key != null && seen.add(key)) {
                collected.add(hit);
                if (collected.size() >= TrendingCatalog.PAGE_SIZE) {
                    return;
                }
            }
        }
    }

    private boolean shouldFallbackToSearch(TrendingCatalog.Category category, List<TrendingHit> collected) {
        if (!CategoryRelevance.isDepartmentChild(category.key())) {
            return false;
        }
        if (!StringUtils.hasText(category.meeshoQuery())) {
            return false;
        }
        // Shared Men fashion leaves (shirts/jackets/…) are women-dominated on Tingily —
        // fill from search until we have a full page of positive Men matches.
        if (CategoryRelevance.requiresPositiveDepartmentMatch(category.key())) {
            return collected.size() < TrendingCatalog.PAGE_SIZE;
        }
        return collected.size() < TrendingCatalog.PAGE_SIZE / 2;
    }

    private List<TrendingHit> searchFallback(TrendingCatalog.Category category, int page) {
        try {
            return openAiMeeshoLookup.search(category.meeshoQuery(), page);
        } catch (ApiException ex) {
            log.warn("Meesho OpenAI fallback unavailable category={} {}", category.key(), ex.getMessage());
            return List.of();
        } catch (Exception ex) {
            log.warn("Meesho OpenAI fallback failed category={} {}", category.key(), ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private JsonNode fetchCatalog(String slug, int limit, int page) throws Exception {
        String token = tingilyAuthService.bearerToken();
        String url = tingilyAuthService.baseUrl()
                + "/api/products/c/" + slug + "/best-sellers?limit=" + limit + "&page=" + page;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "CatalogStudio/1.0")
                .GET();
        if (StringUtils.hasText(token)) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Meesho catalog HTTP {} slug={} auth={}",
                    response.statusCode(), slug, StringUtils.hasText(token));
            throw ApiException.unavailable("Meesho listings are not available for this category right now.");
        }
        return objectMapper.readTree(response.body() == null ? "{}" : response.body());
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

    static List<TrendingHit> parse(JsonNode root, int max) {
        List<TrendingHit> preferred = new ArrayList<>();
        List<TrendingHit> fallback = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            data = root.path("products");
        }
        for (JsonNode item : data) {
            TrendingHit hit = hit(item);
            if (hit == null) {
                continue;
            }
            String key = productKey(hit.productUrl(), hit.externalId());
            if (!seen.add(key)) {
                continue;
            }
            if (OpenAiMeeshoLookup.usableCard(hit.title(), hit.imageUrl())) {
                preferred.add(hit);
            } else {
                fallback.add(hit);
            }
            if (preferred.size() >= max) {
                break;
            }
        }
        if (preferred.size() >= max) {
            return preferred.subList(0, max);
        }
        List<TrendingHit> merged = new ArrayList<>(preferred);
        for (TrendingHit hit : fallback) {
            merged.add(hit);
            if (merged.size() >= max) {
                break;
            }
        }
        return merged;
    }

    private static TrendingHit hit(JsonNode item) {
        String link = text(item, "link");
        if (link == null) {
            link = text(item, "url");
        }
        if (link == null || !link.contains("meesho.com")) {
            return null;
        }
        String title = text(item, "title");
        if (title == null) {
            title = text(item, "name");
        }
        if (title == null || title.toLowerCase(Locale.ROOT).contains("buy premium")) {
            return null;
        }
        String id = text(item, "hero_pid");
        if (id == null) {
            id = text(item, "id");
        }
        if (id == null) {
            id = productKey(link, null);
        }
        if (id == null) {
            return null;
        }
        String image = preferredImage(item);
        String productUrl = link.startsWith("http") ? link : "https://www.meesho.com" + link;
        return new TrendingHit(
                TrendingText.clip(id, 128),
                TrendingText.clip(title, 500),
                text(item, "brand"),
                TrendingText.rupee(text(item, "price")),
                null,
                rating(item.path("avg_rating")),
                reviews(item),
                image,
                productUrl);
    }

    private static String preferredImage(JsonNode item) {
        String image = text(item, "image");
        if (image == null) {
            image = text(item, "image_url");
        }
        if (image == null || !image.startsWith("https://")) {
            return null;
        }
        return upgradeMeeshoImage(image);
    }

    /** Prefer sharper Meesho CDN variants; keep product path (not catalog cover crops). */
    static String upgradeMeeshoImage(String image) {
        if (image == null || image.isBlank()) {
            return image;
        }
        String upgraded = image.replaceAll("(?i)_512\\.(jpe?g|webp|png|avif)$", "_800.$1");
        if (upgraded.contains("/images/catalogs/") && upgraded.contains("/cover/")) {
            // Catalog covers are often extreme crops; keep but callers may still filter.
            return upgraded;
        }
        return upgraded;
    }

    private static String productKey(String link, String fallback) {
        if (link != null) {
            int at = link.lastIndexOf("/p/");
            if (at >= 0 && at + 3 < link.length()) {
                String slug = link.substring(at + 3);
                int cut = slug.indexOf('?');
                if (cut >= 0) {
                    slug = slug.substring(0, cut);
                }
                cut = slug.indexOf('/');
                if (cut >= 0) {
                    slug = slug.substring(0, cut);
                }
                if (!slug.isBlank()) {
                    return slug.toLowerCase(Locale.ROOT);
                }
            }
        }
        return fallback == null ? null : fallback.toLowerCase(Locale.ROOT);
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
        if (node.isNumber()) {
            return node.asText();
        }
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }
}
