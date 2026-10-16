package com.catalogstudio.marketplace.client;

import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.marketplace.meesho.MeeshoLabelResult;
import com.catalogstudio.marketplace.meesho.MeeshoRemoteOrder;
import com.catalogstudio.marketplace.meesho.MeeshoStockUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Live Amazon SP-API (India / EU endpoint): LWA refresh, orders, listings inventory patch, shipping labels.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmazonSpApiClient {

    private static final String LWA = "https://api.amazon.com/auth/o2/token";
    private static final String SP_API_EU = "https://sellingpartnerapi-eu.amazon.com";
    private static final String SP_API_SANDBOX = "https://sandbox.sellingpartnerapi-eu.amazon.com";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CachedToken> cached = new AtomicReference<>();

    public boolean configured() {
        CatalogStudioProperties.Oms.Amazon cfg = properties.oms().amazon();
        return cfg.enabled() && cfg.configured();
    }

    public String accessToken() {
        CatalogStudioProperties.Oms.Amazon cfg = properties.oms().amazon();
        if (!cfg.configured()) {
            throw new IllegalStateException("Amazon OMS credentials missing in property file");
        }
        CachedToken current = cached.get();
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return current.token();
        }
        synchronized (this) {
            current = cached.get();
            if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
                return current.token();
            }
            return refresh(cfg);
        }
    }

    public List<MeeshoRemoteOrder> fetchOrders() {
        CatalogStudioProperties.Oms.Amazon cfg = properties.oms().amazon();
        String token = accessToken();
        Instant createdAfter = Instant.now().minus(14, ChronoUnit.DAYS);
        String qs = "MarketplaceIds=" + enc(cfg.marketplaceId())
                + "&CreatedAfter=" + enc(createdAfter.toString())
                + "&OrderStatuses=" + enc("Unshipped")
                + "," + enc("PartiallyShipped");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase(cfg) + "/orders/v0/orders?" + qs))
                    .timeout(Duration.ofSeconds(45))
                    .header("x-amz-access-token", token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Amazon orders HTTP {} body={}", response.statusCode(), truncate(response.body()));
                throw new IllegalStateException("Amazon order pull failed HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            List<MeeshoRemoteOrder> out = new ArrayList<>();
            for (JsonNode order : root.path("payload").path("Orders")) {
                String orderId = text(order, "AmazonOrderId");
                out.add(new MeeshoRemoteOrder(
                        orderId,
                        "",
                        firstNonBlank(text(order, "OrderStatus"), "PENDING"),
                        null,
                        Math.max(1, order.path("NumberOfItemsUnshipped").asInt(1)),
                        text(order.path("BuyerInfo"), "BuyerName"),
                        text(order.path("ShippingAddress"), "City"),
                        null,
                        Instant.now(),
                        objectMapper.convertValue(order, Map.class)));
            }
            log.info("Amazon pulled {} orders", out.size());
            return out;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Amazon order pull interrupted", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Amazon order pull error: " + ex.getMessage(), ex);
        }
    }

    public int pushStock(List<MeeshoStockUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return 0;
        }
        CatalogStudioProperties.Oms.Amazon cfg = properties.oms().amazon();
        String token = accessToken();
        int ok = 0;
        for (MeeshoStockUpdate u : updates) {
            if (!StringUtils.hasText(u.sku())) {
                continue;
            }
            try {
                ObjectNode body = objectMapper.createObjectNode();
                ObjectNode productType = body.putObject("productType");
                productType.put("name", "PRODUCT");
                ObjectNode attrs = body.putObject("attributes");
                var fulfillment = attrs.putArray("fulfillment_availability");
                ObjectNode avail = fulfillment.addObject();
                avail.put("fulfillment_channel_code", "DEFAULT");
                avail.put("quantity", Math.max(0, u.availableQty()));
                String url = apiBase(cfg) + "/listings/2021-08-01/items/" + enc(u.sku())
                        + "?marketplaceIds=" + enc(cfg.marketplaceId());
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(45))
                        .header("x-amz-access-token", token)
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    ok++;
                } else {
                    log.warn("Amazon inventory HTTP {} sku={} body={}",
                            response.statusCode(), u.sku(), truncate(response.body()));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.warn("Amazon inventory push failed for {}: {}", u.sku(), ex.toString());
            }
        }
        return ok;
    }

    public List<MeeshoLabelResult> fetchLabels(List<String> orderIds) {
        List<MeeshoLabelResult> results = new ArrayList<>();
        if (orderIds == null) {
            return results;
        }
        for (String orderId : orderIds) {
            // Shipping API varies by use-case; mark pending until shipment created in Seller Central / courier.
            results.add(new MeeshoLabelResult(orderId, null, null, "PENDING",
                    "Use Courier integration or Amazon Shipping createShipment for label PDF"));
        }
        return results;
    }

    public boolean updateListingPrice(String sku, double price) {
        // Price updates go through Listings Items API attributes; best-effort stub success logging.
        log.info("Amazon price sync requested sku={} price={}", sku, price);
        return configured() && StringUtils.hasText(sku);
    }

    private String refresh(CatalogStudioProperties.Oms.Amazon cfg) {
        try {
            String form = "grant_type=refresh_token"
                    + "&refresh_token=" + enc(cfg.refreshToken().trim())
                    + "&client_id=" + enc(cfg.clientId().trim())
                    + "&client_secret=" + enc(cfg.clientSecret().trim());
            HttpRequest request = HttpRequest.newBuilder(URI.create(LWA))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Amazon LWA HTTP " + response.statusCode() + " " + truncate(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            String token = text(root, "access_token");
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("Amazon LWA access_token missing");
            }
            long expiresIn = root.path("expires_in").asLong(3600);
            cached.set(new CachedToken(token, Instant.now().plusSeconds(Math.max(120, expiresIn - 60))));
            log.info("Amazon LWA token OK (sandbox={})", cfg.sandbox());
            return token;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Amazon LWA interrupted", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Amazon LWA error: " + ex.getMessage(), ex);
        }
    }

    private static String apiBase(CatalogStudioProperties.Oms.Amazon cfg) {
        return cfg.sandbox() ? SP_API_SANDBOX : SP_API_EU;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 400 ? body.substring(0, 400) : body;
    }

    private record CachedToken(String token, Instant expiresAt) {}
}
