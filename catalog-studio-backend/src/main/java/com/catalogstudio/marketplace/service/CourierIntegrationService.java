package com.catalogstudio.marketplace.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.marketplace.entity.MarketplaceChannelOrder;
import com.catalogstudio.marketplace.repository.MarketplaceChannelOrderRepository;
import com.catalogstudio.security.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Courier integrations beyond marketplace labels (Shiprocket / Delhivery / MANUAL).
 * Credentials: SHIPROCKET_* / DELHIVERY_* in property file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourierIntegrationService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    private final MarketplaceChannelOrderRepository orderRepository;
    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Map<String, Object> createShipment(UUID orderId, String provider) {
        Long userId = SecurityUtils.currentUserId();
        MarketplaceChannelOrder order = orderRepository.findByUuidAndUserId(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        String prov = provider == null ? "MANUAL" : provider.trim().toUpperCase();
        Map<String, Object> result = switch (prov) {
            case "SHIPROCKET" -> shiprocket(order);
            case "DELHIVERY" -> delhivery(order);
            case "MARKETPLACE" -> Map.of(
                    "provider", "MARKETPLACE",
                    "status", "USE_CHANNEL_LABELS",
                    "message", "Use OMS Fetch labels for Flipkart/Meesho/Amazon channel labels");
            default -> manual(order);
        };
        jdbcTemplate.update(
                """
                INSERT INTO oms_courier_shipments
                  (uuid, user_id, channel_order_id, provider, status, awb, label_url, tracking_url, raw_json, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), NOW(), NOW())
                """,
                userId,
                order.getId(),
                prov,
                String.valueOf(result.getOrDefault("status", "CREATED")),
                result.get("awb"),
                result.get("labelUrl"),
                result.get("trackingUrl"),
                toJson(result));
        if (result.get("awb") != null) {
            order.setAwb(String.valueOf(result.get("awb")));
            order.setCourierCode(prov);
            order.setTrackingUrl(result.get("trackingUrl") == null ? null : String.valueOf(result.get("trackingUrl")));
            orderRepository.save(order);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listShipments() {
        Long userId = SecurityUtils.currentUserId();
        return jdbcTemplate.queryForList(
                """
                SELECT uuid::text AS id, provider, status, awb, label_url AS "labelUrl",
                       tracking_url AS "trackingUrl", created_at AS "createdAt"
                FROM oms_courier_shipments
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 100
                """,
                userId);
    }

    private Map<String, Object> shiprocket(MarketplaceChannelOrder order) {
        String email = env("SHIPROCKET_EMAIL");
        String password = env("SHIPROCKET_PASSWORD");
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            return Map.of(
                    "provider", "SHIPROCKET",
                    "status", "ERROR",
                    "message", "Set SHIPROCKET_EMAIL / SHIPROCKET_PASSWORD in property file");
        }
        try {
            ObjectNode login = objectMapper.createObjectNode().put("email", email).put("password", password);
            HttpRequest tokenReq = HttpRequest.newBuilder(URI.create("https://apiv2.shiprocket.in/v1/external/auth/login"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(login.toString()))
                    .build();
            HttpResponse<String> tokenRes = HTTP.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            if (tokenRes.statusCode() < 200 || tokenRes.statusCode() >= 300) {
                return Map.of("provider", "SHIPROCKET", "status", "ERROR",
                        "message", "Shiprocket login HTTP " + tokenRes.statusCode());
            }
            String token = objectMapper.readTree(tokenRes.body()).path("token").asText("");
            ObjectNode body = objectMapper.createObjectNode();
            body.put("order_id", order.getExternalOrderId());
            body.put("order_date", Instant.now().toString());
            body.put("billing_customer_name", order.getBuyerName() == null ? "Customer" : order.getBuyerName());
            body.put("billing_city", order.getShippingCity() == null ? "City" : order.getShippingCity());
            body.put("billing_country", "India");
            body.put("billing_pincode", "110001");
            body.put("billing_state", "Delhi");
            body.put("billing_email", "noreply@catalogstudio.in");
            body.put("billing_phone", "9999999999");
            body.put("shipping_is_billing", true);
            body.put("payment_method", "Prepaid");
            body.put("sub_total", 100);
            var items = body.putArray("order_items");
            ObjectNode item = items.addObject();
            item.put("name", order.getSku() == null ? "Item" : order.getSku());
            item.put("sku", order.getSku() == null ? "SKU" : order.getSku());
            item.put("units", order.getQuantity() == null ? 1 : order.getQuantity());
            item.put("selling_price", "100");
            HttpRequest createReq = HttpRequest.newBuilder(
                            URI.create("https://apiv2.shiprocket.in/v1/external/orders/create/adhoc"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> createRes = HTTP.send(createReq, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(createRes.body() == null ? "{}" : createRes.body());
            Map<String, Object> out = new HashMap<>();
            out.put("provider", "SHIPROCKET");
            out.put("status", createRes.statusCode() >= 200 && createRes.statusCode() < 300 ? "CREATED" : "ERROR");
            out.put("awb", root.path("awb_code").asText(null));
            out.put("labelUrl", root.path("label_url").asText(null));
            out.put("trackingUrl", root.path("tracking_url").asText(null));
            out.put("raw", root.toString());
            return out;
        } catch (Exception ex) {
            log.warn("Shiprocket create failed: {}", ex.toString());
            return Map.of("provider", "SHIPROCKET", "status", "ERROR", "message", ex.getMessage());
        }
    }

    private Map<String, Object> delhivery(MarketplaceChannelOrder order) {
        String token = env("DELHIVERY_API_TOKEN");
        if (!StringUtils.hasText(token)) {
            return Map.of(
                    "provider", "DELHIVERY",
                    "status", "ERROR",
                    "message", "Set DELHIVERY_API_TOKEN in property file");
        }
        return Map.of(
                "provider", "DELHIVERY",
                "status", "QUEUED",
                "message", "Delhivery token present — create shipment via Delhivery waybill API with order "
                        + order.getExternalOrderId(),
                "awb", null);
    }

    private Map<String, Object> manual(MarketplaceChannelOrder order) {
        String awb = "MANUAL-" + order.getExternalOrderId();
        order.setAwb(awb);
        order.setCourierCode("MANUAL");
        orderRepository.save(order);
        return Map.of(
                "provider", "MANUAL",
                "status", "CREATED",
                "awb", awb,
                "message", "Manual courier shipment recorded");
    }

    private String env(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
