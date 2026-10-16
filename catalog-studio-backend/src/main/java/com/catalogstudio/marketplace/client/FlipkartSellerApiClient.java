package com.catalogstudio.marketplace.client;

import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.marketplace.meesho.MeeshoLabelResult;
import com.catalogstudio.marketplace.meesho.MeeshoRemoteOrder;
import com.catalogstudio.marketplace.meesho.MeeshoStockUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Live Flipkart Seller API (Self Access): token, shipments filter, inventory update, labels. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlipkartSellerApiClient {

    private static final String BASE = "https://api.flipkart.net";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CachedToken> cached = new AtomicReference<>();

    public boolean configured() {
        CatalogStudioProperties.Oms.Flipkart cfg = properties.oms().flipkart();
        return cfg.enabled() && cfg.configured();
    }

    public String accessToken() {
        CatalogStudioProperties.Oms.Flipkart cfg = properties.oms().flipkart();
        if (!cfg.configured()) {
            throw new IllegalStateException("Flipkart OMS credentials missing in property file");
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
            return login(cfg);
        }
    }

    public List<MeeshoRemoteOrder> fetchOrders() {
        String token = accessToken();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.set("filter", objectMapper.createObjectNode());
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/sellers/v3/shipments/filter"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Flipkart shipments filter HTTP {} body={}", response.statusCode(), truncate(response.body()));
                throw new IllegalStateException("Flipkart order pull failed HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            List<MeeshoRemoteOrder> out = new ArrayList<>();
            for (JsonNode shipment : root.path("shipments")) {
                String shipmentId = text(shipment, "shipmentId");
                String locationId = text(shipment, "locationId");
                for (JsonNode item : shipment.path("orderItems")) {
                    String orderId = firstNonBlank(text(item, "orderId"), shipmentId);
                    String sku = firstNonBlank(text(item, "sku"), text(item, "fsn"));
                    int qty = item.path("quantity").asInt(1);
                    Map<String, Object> raw = objectMapper.convertValue(item, Map.class);
                    raw.put("shipmentId", shipmentId);
                    raw.put("locationId", locationId);
                    out.add(new MeeshoRemoteOrder(
                            orderId,
                            firstNonBlank(text(item, "orderItemId"), ""),
                            firstNonBlank(text(shipment, "status"), text(item, "status"), "PENDING"),
                            sku,
                            Math.max(1, qty),
                            text(shipment.path("buyerDetails"), "firstName"),
                            text(shipment.path("deliveryAddress"), "city"),
                            text(shipment, "trackingId"),
                            Instant.now(),
                            raw));
                }
                if (shipment.path("orderItems").isEmpty() && StringUtils.hasText(shipmentId)) {
                    out.add(new MeeshoRemoteOrder(
                            shipmentId, "", "PENDING", null, 1, null, null,
                            text(shipment, "trackingId"), Instant.now(),
                            objectMapper.convertValue(shipment, Map.class)));
                }
            }
            log.info("Flipkart pulled {} order rows", out.size());
            return out;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Flipkart order pull interrupted", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Flipkart order pull error: " + ex.getMessage(), ex);
        }
    }

    public int pushStock(List<MeeshoStockUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return 0;
        }
        CatalogStudioProperties.Oms.Flipkart cfg = properties.oms().flipkart();
        String locationId = cfg.locationId();
        if (!StringUtils.hasText(locationId)) {
            throw new IllegalStateException("FLIPKART_LOCATION_ID is required for inventory push");
        }
        String token = accessToken();
        int ok = 0;
        for (int i = 0; i < updates.size(); i += 10) {
            List<MeeshoStockUpdate> batch = updates.subList(i, Math.min(i + 10, updates.size()));
            ObjectNode payload = objectMapper.createObjectNode();
            for (MeeshoStockUpdate u : batch) {
                ObjectNode row = payload.putObject(u.sku());
                // product_id required by Flipkart; when unknown, use sku placeholder lookup via details later
                row.put("product_id", u.sku());
                ArrayNode locations = row.putArray("locations");
                ObjectNode loc = locations.addObject();
                loc.put("id", locationId.trim());
                loc.put("inventory", Math.max(0, u.availableQty()));
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(
                                URI.create(BASE + "/sellers/listings/v3/update/inventory"))
                        .timeout(Duration.ofSeconds(45))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    ok += batch.size();
                } else {
                    log.warn("Flipkart inventory HTTP {} body={}", response.statusCode(), truncate(response.body()));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.warn("Flipkart inventory push failed: {}", ex.toString());
            }
        }
        return ok;
    }

    public List<MeeshoLabelResult> fetchLabels(List<String> shipmentIds) {
        if (shipmentIds == null || shipmentIds.isEmpty()) {
            return List.of();
        }
        String token = accessToken();
        List<MeeshoLabelResult> results = new ArrayList<>();
        for (String id : shipmentIds) {
            try {
                String url = BASE + "/sellers/v3/shipments/" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "/labels";
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(45))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
                    String labelUrl = firstNonBlank(
                            text(root, "labelUrl"),
                            text(root.path("label"), "url"),
                            text(root, "pdfUrl"));
                    String awb = firstNonBlank(text(root, "trackingId"), text(root, "awb"));
                    results.add(new MeeshoLabelResult(id, awb, labelUrl,
                            StringUtils.hasText(labelUrl) ? "READY" : "PENDING", null));
                } else {
                    results.add(new MeeshoLabelResult(id, null, null, "ERROR",
                            "HTTP " + response.statusCode()));
                }
            } catch (Exception ex) {
                results.add(new MeeshoLabelResult(id, null, null, "ERROR", ex.getMessage()));
            }
        }
        return results;
    }

    public boolean updateListingPrice(String sku, double price) {
        if (!StringUtils.hasText(sku)) {
            return false;
        }
        String token = accessToken();
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            ObjectNode row = payload.putObject(sku);
            row.put("price", price);
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/sellers/listings/v3/update"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ex) {
            log.warn("Flipkart price update failed for {}: {}", sku, ex.toString());
            return false;
        }
    }

    private String login(CatalogStudioProperties.Oms.Flipkart cfg) {
        try {
            String basic = Base64.getEncoder().encodeToString(
                    (cfg.appId().trim() + ":" + cfg.appSecret().trim()).getBytes(StandardCharsets.UTF_8));
            String url = BASE + "/oauth-service/oauth/token?grant_type=client_credentials&scope="
                    + URLEncoder.encode("Seller_Api", StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Basic " + basic)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Flipkart token HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            String token = text(root, "access_token");
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("Flipkart token missing in response");
            }
            long expiresIn = root.path("expires_in").asLong(3600);
            cached.set(new CachedToken(token, Instant.now().plusSeconds(Math.max(120, expiresIn - 60))));
            log.info("Flipkart Seller API token OK");
            return token;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Flipkart token interrupted", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Flipkart token error: " + ex.getMessage(), ex);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
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
