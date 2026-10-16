package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Verifies a GSTIN and checks that the registered legal / trade name matches the store name.
 *
 * <p>Providers ({@code catalogstudio.wholesale.gst.provider}):
 * <ul>
 *   <li>{@code mock} — local/dev format check only</li>
 *   <li>{@code gstinapi} — gstinapi.in GET /v1/gstin/{gstin} (production path)</li>
 *   <li>{@code http} — generic GET/POST registry API</li>
 * </ul>
 */
@Slf4j
@Service
public class GstVerificationService {

    private static final Pattern GSTIN_FORMAT =
            Pattern.compile("^(0[1-9]|[1-2][0-9]|3[0-8]|97|99)[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final String CHECKSUM_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Set<String> NOISE_TOKENS = Set.of(
            "PVT", "PRIVATE", "LTD", "LIMITED", "LLP", "OPC", "INC", "THE");
    private static final Set<String> INACTIVE_STATUSES = Set.of("CANCELLED", "CANCELED", "INACTIVE", "SUSPENDED");
    private static final String[] LEGAL_KEYS = {"legal_name", "legalName", "lgnm", "legal_name_of_business"};
    private static final String[] TRADE_KEYS = {"trade_name", "tradeName", "tradeNam", "trade_name_of_business"};
    private static final String[] STATUS_KEYS = {"gstin_status", "gstinStatus", "sts", "status"};

    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public GstVerificationService(CatalogStudioProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record GstVerificationResult(
            String gstin,
            String legalName,
            String tradeName,
            String matchedName,
            double similarity
    ) {
        /** Name persisted on the store (legal name preferred). */
        public String registeredName() {
            return StringUtils.hasText(legalName) ? legalName : tradeName;
        }
    }

    public static String normalizeGstin(String gstin) {
        return gstin == null ? "" : gstin.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public static boolean isValidFormat(String gstin) {
        return gstin != null && GSTIN_FORMAT.matcher(gstin).matches();
    }

    /** Official GSTIN mod-36 check digit. */
    public static boolean hasValidChecksum(String gstin) {
        if (!isValidFormat(gstin)) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int value = CHECKSUM_CHARS.indexOf(gstin.charAt(i));
            int product = value * (i % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        int check = (36 - (sum % 36)) % 36;
        return CHECKSUM_CHARS.charAt(check) == gstin.charAt(14);
    }

    /**
     * Looks up GST registry details and returns the locked store name:
     * trade name when present, otherwise legal name.
     */
    public GstLookupNames lookup(String rawGstin) {
        String gstin = normalizeGstin(rawGstin);
        if (!isValidFormat(gstin)) {
            throw ApiException.badRequest("Enter a valid 15-character GSTIN (for example 27AAPFU0939F1ZV)");
        }
        CatalogStudioProperties.Wholesale.Gst config = properties.wholesale().gst();
        if (config.mockProvider()) {
            String mockName = "MOCK TRADE " + gstin.substring(0, 5);
            return new GstLookupNames(gstin, mockName + " LEGAL", mockName, mockName, "Active");
        }
        if (!hasValidChecksum(gstin)) {
            throw ApiException.badRequest("GSTIN check digit is invalid. Please re-check the number.");
        }
        Registered registered = config.gstinapiProvider()
                ? fetchGstinApi(gstin, config)
                : fetchHttp(gstin, config);
        String storeName = preferredStoreName(registered.legalName(), registered.tradeName());
        return new GstLookupNames(
                gstin,
                registered.legalName(),
                registered.tradeName(),
                storeName,
                "Active");
    }

    /** Prefer trade name for the public store name; fall back to legal name. */
    public static String preferredStoreName(String legalName, String tradeName) {
        if (StringUtils.hasText(tradeName)) {
            return tradeName.trim();
        }
        if (StringUtils.hasText(legalName)) {
            return legalName.trim();
        }
        throw ApiException.badRequest("GST registry did not return a registered name for this GSTIN");
    }

    public GstVerificationResult verify(String rawGstin, String storeName) {
        GstLookupNames lookup = lookup(rawGstin);
        if (!StringUtils.hasText(storeName)) {
            throw ApiException.badRequest("Store name is required");
        }
        CatalogStudioProperties.Wholesale.Gst config = properties.wholesale().gst();
        if (config.mockProvider()) {
            return new GstVerificationResult(lookup.gstin(), lookup.legalName(), lookup.tradeName(), storeName.trim(), 1.0);
        }
        return matchName(
                lookup.gstin(),
                storeName,
                lookup.legalName(),
                lookup.tradeName(),
                config.nameMatchThreshold());
    }

    public record GstLookupNames(
            String gstin,
            String legalName,
            String tradeName,
            String storeName,
            String status
    ) {}

    public GstVerificationResult matchName(
            String gstin, String storeName, String legalName, String tradeName, double threshold) {
        double legalScore = StringUtils.hasText(legalName) ? similarity(storeName, legalName) : 0;
        double tradeScore = StringUtils.hasText(tradeName) ? similarity(storeName, tradeName) : 0;
        if (!StringUtils.hasText(legalName) && !StringUtils.hasText(tradeName)) {
            throw ApiException.badRequest("GST registry did not return a registered name for this GSTIN");
        }
        boolean tradeBetter = tradeScore > legalScore;
        double best = Math.max(legalScore, tradeScore);
        if (best < threshold) {
            String shown = StringUtils.hasText(legalName) ? legalName : tradeName;
            throw ApiException.badRequest(
                    "Store name does not match the GST registration (" + shown
                            + "). Use your registered legal or trade name as the store name.");
        }
        return new GstVerificationResult(
                gstin, legalName, tradeName, tradeBetter ? tradeName : legalName, best);
    }

    // --- name similarity ------------------------------------------------------------------

    public static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        String upper = value.toUpperCase(Locale.ROOT).trim().replaceFirst("^M\\s*/\\s*S\\.?\\s+", "");
        String cleaned = upper.replace("&", " AND ").replaceAll("[^A-Z0-9]+", " ").trim();
        StringBuilder sb = new StringBuilder();
        for (String token : cleaned.split("\\s+")) {
            if (token.isEmpty() || NOISE_TOKENS.contains(token)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(token);
        }
        return sb.toString();
    }

    public static double similarity(String a, String b) {
        String na = normalizeName(a);
        String nb = normalizeName(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return 0;
        }
        if (na.equals(nb)) {
            return 1.0;
        }
        String ca = na.replace(" ", "");
        String cb = nb.replace(" ", "");
        if (ca.equals(cb)) {
            return 1.0;
        }
        return Math.max(dice(ca, cb), jaccard(na, nb));
    }

    private static double dice(String a, String b) {
        if (a.length() < 2 || b.length() < 2) {
            return a.equals(b) ? 1.0 : 0;
        }
        java.util.Map<String, Integer> bigrams = new java.util.HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) {
            bigrams.merge(a.substring(i, i + 2), 1, Integer::sum);
        }
        int intersection = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String bg = b.substring(i, i + 2);
            Integer count = bigrams.get(bg);
            if (count != null && count > 0) {
                bigrams.put(bg, count - 1);
                intersection++;
            }
        }
        return (2.0 * intersection) / ((a.length() - 1) + (b.length() - 1));
    }

    private static double jaccard(String a, String b) {
        Set<String> ta = new HashSet<>(Arrays.asList(a.split(" ")));
        Set<String> tb = new HashSet<>(Arrays.asList(b.split(" ")));
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        ta.retainAll(tb);
        return union.isEmpty() ? 0 : (double) ta.size() / union.size();
    }

    // --- gstinapi.in ----------------------------------------------------------------------

    private Registered fetchGstinApi(String gstin, CatalogStudioProperties.Wholesale.Gst config) {
        if (!config.gstinapiConfigured()) {
            throw ApiException.unavailable(
                    "GSTINAPI is not configured. Set GSTINAPI_API_KEY in the environment.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.gstinapiLookupUrl(gstin)))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("x-api-key", config.gstinapiApiKey().trim())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw ApiException.badRequest("GSTIN was not found in the GST registry");
            }
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("GSTINAPI auth failed HTTP {}", response.statusCode());
                throw ApiException.unavailable("GST verification credentials are invalid. Contact support.");
            }
            if (response.statusCode() == 402) {
                throw ApiException.unavailable("GST verification credits are exhausted. Please top up GSTINAPI.");
            }
            if (response.statusCode() / 100 != 2) {
                log.warn("GSTINAPI returned HTTP {} body={}", response.statusCode(), truncate(response.body()));
                throw ApiException.unavailable("GST verification is temporarily unavailable. Please try again.");
            }
            return parseGstinApiResponse(objectMapper.readTree(response.body()));
        } catch (ApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("GST verification was interrupted. Please try again.");
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("GSTINAPI call failed: {}", ex.toString());
            throw ApiException.unavailable("GST verification is temporarily unavailable. Please try again.");
        }
    }

    /** Parses gstinapi.in GET /v1/gstin/{gstin} JSON. */
    public static Registered parseGstinApiResponse(JsonNode root) {
        if (root == null || root.isNull()) {
            throw ApiException.unavailable("GST verification returned an empty response");
        }
        if (root.has("success") && root.get("success").isBoolean() && !root.get("success").asBoolean()) {
            String message = text(root.get("message"));
            if (StringUtils.hasText(message) && message.toLowerCase(Locale.ROOT).contains("invalid")) {
                throw ApiException.badRequest("GSTIN was not found in the GST registry");
            }
            throw ApiException.badRequest(StringUtils.hasText(message) ? message : "GSTIN verification failed");
        }
        String status = text(root.get("status"));
        if (StringUtils.hasText(status)) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if (INACTIVE_STATUSES.contains(normalized)
                    || normalized.contains("CANCEL")
                    || normalized.contains("SUSPEND")
                    || normalized.contains("INACTIV")) {
                throw ApiException.badRequest("This GSTIN is not active (" + status + ")");
            }
        }
        String legalName = text(root.get("legal_name"));
        String tradeName = text(root.get("trade_name"));
        if (!StringUtils.hasText(legalName) && !StringUtils.hasText(tradeName)) {
            throw ApiException.badRequest("GST registry did not return a registered name for this GSTIN");
        }
        return new Registered(legalName, tradeName);
    }

    // --- Generic HTTP provider ------------------------------------------------------------

    private Registered fetchHttp(String gstin, CatalogStudioProperties.Wholesale.Gst config) {
        if (!StringUtils.hasText(config.httpUrl())) {
            throw ApiException.unavailable("GST verification is not configured. Please contact support.");
        }
        try {
            HttpRequest.Builder builder;
            String url = config.httpUrl().trim();
            if (url.contains("{gstin}")) {
                builder = HttpRequest.newBuilder(URI.create(
                        url.replace("{gstin}", URLEncoder.encode(gstin, StandardCharsets.UTF_8)))).GET();
            } else {
                builder = HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(java.util.Map.of("gstin", gstin))));
            }
            builder.timeout(Duration.ofSeconds(12)).header("Accept", "application/json");
            if (StringUtils.hasText(config.httpApiKey())) {
                builder.header("Authorization", "Bearer " + config.httpApiKey().trim());
                builder.header("x-api-key", config.httpApiKey().trim());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw ApiException.badRequest("GSTIN was not found in the GST registry");
            }
            if (response.statusCode() / 100 != 2) {
                log.warn("GST provider returned HTTP {}", response.statusCode());
                throw ApiException.unavailable("GST verification is temporarily unavailable. Please try again.");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String status = findText(root, STATUS_KEYS);
            if (status != null && INACTIVE_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT))) {
                throw ApiException.badRequest("This GSTIN is not active (" + status + ")");
            }
            return new Registered(findText(root, LEGAL_KEYS), findText(root, TRADE_KEYS));
        } catch (ApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("GST verification was interrupted. Please try again.");
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("GST provider call failed: {}", ex.toString());
            throw ApiException.unavailable("GST verification is temporarily unavailable. Please try again.");
        }
    }

    public record Registered(String legalName, String tradeName) {}

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 240 ? body : body.substring(0, 240) + "...";
    }

    static String findText(JsonNode root, String... keys) {
        if (root == null) {
            return null;
        }
        Queue<JsonNode> queue = new ArrayDeque<>();
        Queue<Integer> depth = new ArrayDeque<>();
        queue.add(root);
        depth.add(0);
        while (!queue.isEmpty()) {
            JsonNode node = queue.poll();
            int level = depth.poll();
            if (node.isObject()) {
                for (String key : keys) {
                    JsonNode value = node.get(key);
                    if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                        return value.asText().trim();
                    }
                }
                if (level < 4) {
                    node.elements().forEachRemaining(child -> {
                        if (child.isContainerNode()) {
                            queue.add(child);
                            depth.add(level + 1);
                        }
                    });
                }
            } else if (node.isArray() && level < 4) {
                node.elements().forEachRemaining(child -> {
                    if (child.isContainerNode()) {
                        queue.add(child);
                        depth.add(level + 1);
                    }
                });
            }
        }
        return null;
    }
}
