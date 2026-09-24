package com.catalogstudio.trending.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiMeeshoLookup {

    private static final Pattern PRODUCT_ID = Pattern.compile("/p/([A-Za-z0-9]+)");
    private static final Pattern CATALOG_IMAGE = Pattern.compile(
            "https://images\\.meesho\\.com/[^\\s\"'<>\\\\]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern USABLE_IMAGE = Pattern.compile(
            "https://images\\.meesho\\.com/images/products/\\d+/",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STAR_RATING = Pattern.compile("(?<![0-9])([0-5](?:\\.[0-9])?)(?![0-9])");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;

    public List<TrendingHit> search(String query, int page) {
        if (!StringUtils.hasText(properties.ai().apiKey())) {
            throw ApiException.unavailable("Meesho is not accepting requests from this server, and OpenAI is not configured.");
        }
        int safePage = Math.max(page, 1);
        SearchText first = ask(prompt(query, safePage));
        List<TrendingHit> hits = applyPhotos(parse(objectMapper, first.text(), query), first.raw());
        if (needsDetail(hits)) {
            SearchText detail = ask(detailPrompt(query, hits));
            hits = applyPhotos(merge(hits, parse(objectMapper, detail.text(), query)), detail.raw());
        }
        log.info("OpenAI Meesho search query={} page={} products={}", query, safePage, hits.size());
        if (hits.isEmpty()) {
            throw ApiException.unavailable("Meesho blocked this server, and OpenAI web search did not return Meesho listings. Your saved products still open without a new lookup.");
        }
        return hits;
    }

    public static List<TrendingHit> parse(ObjectMapper objectMapper, String content) {
        return parse(objectMapper, content, null);
    }

    public static List<TrendingHit> parse(ObjectMapper objectMapper, String content, String query) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            JsonNode array = objectMapper.readTree(content.substring(start, end + 1));
            if (!array.isArray()) {
                return List.of();
            }
            List<TrendingHit> hits = new ArrayList<>();
            for (JsonNode item : array) {
                TrendingHit hit = hit(item, query);
                if (hit != null) {
                    hits.add(hit);
                }
                if (hits.size() == TrendingCatalog.PAGE_SIZE) {
                    break;
                }
            }
            attachLooseImages(hits, content);
            return hits;
        } catch (Exception ex) {
            return List.of();
        }
    }

    public static boolean usableCard(String title, String imageUrl) {
        return title != null && title.contains(" ")
                && imageUrl != null
                && imageUrl.startsWith("https://")
                && !imageUrl.contains("/200x200/");
    }

    public static List<TrendingHit> dropSingletonImages(List<TrendingHit> hits) {
        long withPhoto = 0;
        for (TrendingHit hit : hits) {
            if (hit.imageUrl() != null) {
                withPhoto++;
            }
        }
        if (withPhoto != 1) {
            return hits;
        }
        List<TrendingHit> cleared = new ArrayList<>();
        for (TrendingHit hit : hits) {
            cleared.add(withImage(hit, null));
        }
        return cleared;
    }

    public static List<TrendingHit> dropRepeatedImages(List<TrendingHit> hits) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TrendingHit hit : hits) {
            if (hit.imageUrl() != null) {
                counts.merge(hit.imageUrl(), 1, Integer::sum);
            }
        }
        List<TrendingHit> unique = new ArrayList<>();
        for (TrendingHit hit : hits) {
            Integer count = hit.imageUrl() == null ? null : counts.get(hit.imageUrl());
            unique.add(count != null && count > 1 ? withImage(hit, null) : hit);
        }
        return unique;
    }

    private List<TrendingHit> verifyImages(List<TrendingHit> hits) {
        List<CompletableFuture<TrendingHit>> checks = new ArrayList<>();
        for (TrendingHit hit : hits) {
            checks.add(CompletableFuture.supplyAsync(() -> imageLoads(hit.imageUrl()) ? hit : withImage(hit, null)));
        }
        List<TrendingHit> checked = new ArrayList<>();
        for (CompletableFuture<TrendingHit> check : checks) {
            try {
                checked.add(check.join());
            } catch (Exception ex) {
                checked.add(withImage(hits.get(checked.size()), null));
            }
        }
        return checked;
    }

    private static boolean imageLoads(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("https://") || imageUrl.contains("/200x200/")) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Range", "bytes=0-64")
                    .GET()
                    .build();
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status != 200 && status != 206) {
                return false;
            }
            String type = response.headers().firstValue("content-type").orElse("");
            return type.startsWith("image/") || type.isBlank();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private SearchText ask(String prompt) {
        try {
            return responsesSearch(prompt);
        } catch (ApiException ex) {
            return chatSearch(prompt);
        }
    }

    private List<TrendingHit> applyPhotos(List<TrendingHit> hits, String raw) {
        List<TrendingHit> withSearch = assignSearchImages(hits, raw);
        List<TrendingHit> withPayload = assignPayloadPhotos(withSearch, raw);
        return dropSingletonImages(dropRepeatedImages(verifyImages(withPayload)));
    }

    public static List<TrendingHit> assignSearchImages(List<TrendingHit> hits, String raw) {
        if (raw == null || raw.isBlank() || hits.isEmpty()) {
            return hits;
        }
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(raw);
        } catch (Exception ex) {
            return hits;
        }
        Map<String, String> byProduct = new LinkedHashMap<>();
        List<String> loose = new ArrayList<>();
        collectImageResults(root, byProduct, loose);
        if (byProduct.isEmpty() && loose.size() < 2) {
            return hits;
        }
        List<TrendingHit> assigned = new ArrayList<>();
        int looseCursor = 0;
        for (TrendingHit hit : hits) {
            String photo = byProduct.get(hit.externalId());
            if (photo == null) {
                while (looseCursor < loose.size() && used(assigned, loose.get(looseCursor))) {
                    looseCursor++;
                }
                if (looseCursor < loose.size()) {
                    photo = loose.get(looseCursor);
                    looseCursor++;
                }
            }
            assigned.add(photo == null ? hit : withImage(hit, photo));
        }
        return assigned;
    }

    private static int countImageResults(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        int count = node.isObject() && "image_result".equals(node.path("type").asText()) ? 1 : 0;
        if (node.isArray() || node.isObject()) {
            for (JsonNode child : node) {
                count += countImageResults(child);
            }
        }
        return count;
    }

    private static void collectImageResults(JsonNode node, Map<String, String> byProduct, List<String> loose) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject() && "image_result".equals(node.path("type").asText())) {
            String photo = firstHttpsImage(node, "image_url", "thumbnail_url");
            String source = node.path("source_website_url").asText("");
            Matcher product = PRODUCT_ID.matcher(source);
            if (photo != null && product.find()) {
                byProduct.putIfAbsent(product.group(1), photo);
            } else if (photo != null && !loose.contains(photo)) {
                loose.add(photo);
            }
            return;
        }
        if (node.isArray() || node.isObject()) {
            node.forEach(child -> collectImageResults(child, byProduct, loose));
        }
    }

    private static String firstHttpsImage(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (value.startsWith("https://")) {
                return value;
            }
        }
        return null;
    }

    public static List<TrendingHit> assignPayloadPhotos(List<TrendingHit> hits, String raw) {
        if (raw == null || raw.isBlank() || hits.isEmpty()) {
            return hits;
        }
        List<String> photos = new ArrayList<>();
        Matcher matcher = CATALOG_IMAGE.matcher(raw);
        while (matcher.find()) {
            String photo = matcher.group();
            if (!photos.contains(photo)) {
                photos.add(photo);
            }
        }
        if (photos.size() < 2) {
            return hits;
        }
        List<TrendingHit> assigned = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            String photo = i < photos.size() ? photos.get(i) : hits.get(i).imageUrl();
            assigned.add(withImage(hits.get(i), photo));
        }
        return assigned;
    }

    private ObjectNode previewBody(String prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.ai().model());
        ObjectNode tool = body.putArray("tools").addObject();
        tool.put("type", "web_search_preview");
        tool.put("search_context_size", "high");
        tool.putObject("user_location").put("type", "approximate").put("country", "IN");
        body.putArray("include").add("web_search_call.action.sources");
        body.put("input", prompt);
        return body;
    }

    private SearchText responsesSearch(String prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "gpt-4o");
        ArrayNode tools = body.putArray("tools");
        ObjectNode tool = tools.addObject();
        tool.put("type", "web_search");
        tool.put("search_context_size", "high");
        ArrayNode contentTypes = tool.putArray("search_content_types");
        contentTypes.add("text");
        contentTypes.add("image");
        ObjectNode imageSettings = tool.putObject("image_settings");
        imageSettings.put("max_results", TrendingCatalog.PAGE_SIZE);
        imageSettings.put("caption", true);
        tool.putObject("user_location").put("type", "approximate").put("country", "IN");
        ArrayNode include = body.putArray("include");
        include.add("web_search_call.results");
        include.add("web_search_call.action.sources");
        body.put("input", prompt);
        JsonNode root;
        try {
            root = post("https://api.openai.com/v1/responses", body);
        } catch (ApiException ex) {
            root = post("https://api.openai.com/v1/responses", previewBody(prompt));
        }
        String raw = root.toString();
        log.info(
                "OpenAI Meesho image results={} meeshoCdn={} imageUrlField={}",
                countImageResults(root),
                raw.contains("images.meesho.com"),
                raw.contains("image_url"));
        int cdnAt = raw.indexOf("images.meesho.com");
        if (cdnAt >= 0) {
            int start = Math.max(0, cdnAt - 12);
            int end = Math.min(raw.length(), cdnAt + 160);
            log.info("Meesho CDN snippet {}", raw.substring(start, end).replaceAll("\\s+", " "));
        }
        if (countImageResults(root) == 0 && !raw.contains("/p/")) {
            root = post("https://api.openai.com/v1/responses", previewBody(prompt));
            raw = root.toString();
        }
        if (root.hasNonNull("output_text") && !root.get("output_text").asText().isBlank()) {
            return new SearchText(root.get("output_text").asText(), raw);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            for (JsonNode part : item.path("content")) {
                if (part.hasNonNull("text")) {
                    text.append(part.get("text").asText());
                }
            }
        }
        if (text.isEmpty()) {
            throw ApiException.unavailable("OpenAI web search returned no Meesho text");
        }
        return new SearchText(text.toString(), raw);
    }

    private SearchText chatSearch(String prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "gpt-4o-mini-search-preview");
        body.putObject("web_search_options");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);
        JsonNode root = post("https://api.openai.com/v1/chat/completions", body);
        String text = root.path("choices").path(0).path("message").path("content").asText("");
        if (text.isBlank()) {
            throw ApiException.unavailable("OpenAI web search returned no Meesho text");
        }
        return new SearchText(text, root.toString());
    }

    private record SearchText(String text, String raw) {}

    private JsonNode post(String url, ObjectNode body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(properties.ai().timeoutSeconds(), 45)))
                    .header("Authorization", "Bearer " + properties.ai().apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = response.body() == null ? "" : response.body();
                if (detail.length() > 400) {
                    detail = detail.substring(0, 400);
                }
                log.warn("OpenAI Meesho search HTTP {} {}", response.statusCode(), detail);
                throw ApiException.unavailable("OpenAI web search could not read Meesho");
            }
            return objectMapper.readTree(response.body() == null ? "{}" : response.body());
        } catch (ApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("OpenAI web search could not read Meesho");
        } catch (Exception ex) {
            log.warn("OpenAI Meesho search failed: {}", ex.getClass().getSimpleName());
            throw ApiException.unavailable("OpenAI web search could not read Meesho");
        }
    }

    private static String prompt(String query, int page) {
        int start = ((page - 1) * TrendingCatalog.PAGE_SIZE) + 1;
        return """
                Search the live Meesho India site for "%s" and open only product cards that match that search.
                The shopper and the garment are both required. If the search starts with Men, every product must be men's wear. If it starts with Women, every product must be women's wear. If it starts with Kids, every product must be kids' wear.
                Do not return a different garment. A search for kurta sets must be kurta sets, not dresses, shorts, tops, or western wear.
                Skip until result %d and return the next %d matching products that you can verify.
                Reply with a JSON array only. Each object must have title, price, rating, reviews, imageUrl, productUrl.
                title is the full product name on the card, at least three words, and it must name the garment. Never reply with only the search word.
                price is the rupee selling price, like ₹499.
                rating is the star score only, like 4.2. reviews is the ratings and reviews line, like "215 Ratings, 71 Reviews".
                imageUrl is that product's own photo src. It is hosted on images.meesho.com, the folder after /images/products/ is a number, and the file usually ends in .avif. Each product has a different imageUrl. If you did not see that src, use null. Do not copy one photo onto other products, and do not invent a .jpg.
                productUrl must be the https://www.meesho.com/.../p/... link for that same product.
                Do not invent products, prices, photos, or links. Omit a product you cannot verify.
                """.formatted(query, start, TrendingCatalog.PAGE_SIZE);
    }

    private static String detailPrompt(String query, List<TrendingHit> hits) {
        StringBuilder urls = new StringBuilder();
        for (TrendingHit hit : hits) {
            urls.append("- ").append(hit.productUrl()).append('\n');
        }
        return """
                Open each of these live Meesho product pages for "%s" and copy the fields shown on that page.
                Reply with a JSON array only. One object per URL, with title, price, rating, reviews, imageUrl, productUrl.
                title is the product name on the page, not the search word.
                rating is the star score only, like 3.9. reviews is the line with ratings and reviews, like "215 Ratings, 71 Reviews".
                imageUrl is that page's own photo src on images.meesho.com, under /images/products/ and a numeric folder, usually ending in .avif. Every productUrl must have a different imageUrl. Use null if you cannot see the src. Do not repeat a photo.
                productUrl is the same page URL. Do not invent a photo or a rating. Use null when the page does not show that field.
                %s
                """.formatted(query, urls);
    }

    private static boolean needsDetail(List<TrendingHit> hits) {
        if (hits.isEmpty()) {
            return false;
        }
        for (TrendingHit hit : hits) {
            if (hit.imageUrl() == null || hit.rating() == null || hit.reviewCount() == null) {
                return true;
            }
        }
        return false;
    }

    static List<TrendingHit> merge(List<TrendingHit> primary, List<TrendingHit> detail) {
        Map<String, TrendingHit> byId = new LinkedHashMap<>();
        for (TrendingHit hit : primary) {
            byId.put(hit.externalId(), hit);
        }
        for (TrendingHit extra : detail) {
            TrendingHit current = byId.get(extra.externalId());
            byId.put(extra.externalId(), current == null ? extra : prefer(current, extra));
        }
        List<TrendingHit> merged = new ArrayList<>();
        for (TrendingHit hit : byId.values()) {
            if (hit.title() != null) {
                merged.add(hit);
            }
            if (merged.size() == TrendingCatalog.PAGE_SIZE) {
                break;
            }
        }
        return merged;
    }

    private static TrendingHit prefer(TrendingHit current, TrendingHit extra) {
        return new TrendingHit(
                current.externalId(),
                longer(extra.title(), current.title()),
                current.brand() != null ? current.brand() : extra.brand(),
                current.priceLabel() != null ? current.priceLabel() : extra.priceLabel(),
                current.mrpLabel() != null ? current.mrpLabel() : extra.mrpLabel(),
                current.rating() != null ? current.rating() : extra.rating(),
                current.reviewCount() != null ? current.reviewCount() : extra.reviewCount(),
                current.imageUrl() != null ? current.imageUrl() : extra.imageUrl(),
                current.productUrl() != null ? current.productUrl() : extra.productUrl());
    }

    private static String longer(String preferred, String fallback) {
        if (preferred == null) {
            return fallback;
        }
        if (fallback == null || preferred.length() >= fallback.length()) {
            return preferred;
        }
        return fallback;
    }

    private static TrendingHit hit(JsonNode item, String query) {
        String url = text(item, "productUrl");
        if (url == null || !url.contains("meesho.com")) {
            return null;
        }
        Matcher id = PRODUCT_ID.matcher(url);
        if (!id.find()) {
            return null;
        }
        String title = chooseTitle(text(item, "title"), url, query);
        if (title == null) {
            return null;
        }
        String productUrl = url.startsWith("http") ? url : "https://www.meesho.com" + url;
        return new TrendingHit(
                TrendingText.clip(id.group(1), 128),
                TrendingText.clip(title, 500),
                null,
                TrendingText.rupee(text(item, "price")),
                null,
                rating(text(item, "rating")),
                reviews(text(item, "reviews")),
                catalogImage(text(item, "imageUrl")),
                productUrl);
    }

    static String chooseTitle(String modelTitle, String url, String query) {
        String slugTitle = titleFromProductUrl(url);
        if (weakTitle(modelTitle, query)) {
            return slugTitle;
        }
        String cleaned = modelTitle.trim().replaceAll("\\s+", " ");
        if (slugTitle != null && slugTitle.length() > cleaned.length() + 8) {
            return slugTitle;
        }
        return cleaned;
    }

    static String titleFromProductUrl(String url) {
        if (url == null) {
            return null;
        }
        int product = url.indexOf("/p/");
        if (product <= 0) {
            return null;
        }
        String path = url.substring(0, product);
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash >= path.length() - 1) {
            return null;
        }
        String[] words = path.substring(slash + 1).split("-");
        if (words.length < 2) {
            return null;
        }
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (title.length() > 0) {
                title.append(' ');
            }
            title.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                title.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        String result = title.toString();
        return result.contains(" ") ? result : null;
    }

    private static boolean weakTitle(String title, String query) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String cleaned = title.trim().replaceAll("\\s+", " ");
        if (query != null && cleaned.equalsIgnoreCase(query.trim())) {
            return true;
        }
        return !cleaned.contains(" ");
    }

    public static String catalogImage(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = CATALOG_IMAGE.matcher(raw.trim());
        if (!matcher.find()) {
            return null;
        }
        return matcher.group();
    }

    private static String rating(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = STAR_RATING.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private static String reviews(String raw) {
        String clipped = TrendingText.clip(raw, 32);
        if (clipped == null || !clipped.matches(".*\\d.*")) {
            return null;
        }
        return clipped;
    }

    private static void attachLooseImages(List<TrendingHit> hits, String content) {
        List<String> images = new ArrayList<>();
        Matcher matcher = CATALOG_IMAGE.matcher(content);
        while (matcher.find()) {
            String image = matcher.group();
            if (!images.contains(image)) {
                images.add(image);
            }
        }
        int cursor = 0;
        for (int i = 0; i < hits.size(); i++) {
            TrendingHit hit = hits.get(i);
            if (hit.imageUrl() != null) {
                continue;
            }
            while (cursor < images.size() && used(hits, images.get(cursor))) {
                cursor++;
            }
            if (cursor >= images.size()) {
                return;
            }
            hits.set(i, withImage(hit, images.get(cursor)));
            cursor++;
        }
    }

    private static boolean used(List<TrendingHit> hits, String image) {
        for (TrendingHit hit : hits) {
            if (image.equals(hit.imageUrl())) {
                return true;
            }
        }
        return false;
    }

    private static TrendingHit withImage(TrendingHit hit, String image) {
        return new TrendingHit(
                hit.externalId(),
                hit.title(),
                hit.brand(),
                hit.priceLabel(),
                hit.mrpLabel(),
                hit.rating(),
                hit.reviewCount(),
                image,
                hit.productUrl());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String raw = value.asText();
        return raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw) ? null : raw.trim();
    }
}
