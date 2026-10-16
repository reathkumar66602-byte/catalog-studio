package com.catalogstudio.marketplace.meesho;

import com.catalogstudio.config.CatalogStudioProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Meesho Supplier Panel session client.
 * Attempts live login + order/stock/label routes used by OMS partners.
 * When Meesho changes private routes, methods degrade with clear logs.
 */
@Slf4j
@Component
public class HttpMeeshoPanelClient implements MeeshoPanelClient {

    private static final List<String> LOGIN_PATHS = List.of(
            "/api/supplier/v1/user/login",
            "/api/v1/user/login",
            "/panel/v3/new/root/api/login");

    private static final List<String> ORDER_PATHS = List.of(
            "/api/supplier/v1/order/new",
            "/api/supplier/orders/pending",
            "/api/v1/orders/pending");

    private static final List<String> STOCK_PATHS = List.of(
            "/api/supplier/v1/inventory/update",
            "/api/v1/inventory/update");

    private static final List<String> LABEL_PATHS = List.of(
            "/api/supplier/v1/order/label",
            "/api/v1/orders/label");

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final boolean liveAuthRequired;

    public HttpMeeshoPanelClient(CatalogStudioProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        String fromOms = properties.oms() != null && properties.oms().meesho() != null
                ? properties.oms().meesho().baseUrl()
                : null;
        CatalogStudioProperties.MeeshoPanel legacy = properties.meeshoPanel();
        String configured = StringUtils.hasText(fromOms)
                ? fromOms
                : (legacy == null ? null : legacy.baseUrl());
        this.baseUrl = !StringUtils.hasText(configured)
                ? "https://supplier.meesho.com"
                : configured.trim().replaceAll("/+$", "");
        boolean omsLive = properties.oms() != null
                && properties.oms().meesho() != null
                && properties.oms().meesho().liveAuthRequired();
        this.liveAuthRequired = omsLive || (legacy != null && legacy.liveAuthRequired());
    }

    @Override
    public AuthResult authenticate(String email, String password) {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient http = client(cookies);
        String lastError = "Meesho panel login endpoints did not accept the request";
        for (String path : LOGIN_PATHS) {
            try {
                ObjectNode body = objectMapper.createObjectNode()
                        .put("email", email.trim())
                        .put("username", email.trim())
                        .put("password", password);
                HttpResponse<String> response = post(http, path, body.toString(), null);
                int code = response.statusCode();
                String responseBody = response.body() == null ? "" : response.body();
                if (code == 401 || code == 403) {
                    return AuthResult.failed("Meesho rejected email/password. Check Supplier Panel login.");
                }
                if (code >= 200 && code < 300 && looksAuthenticated(responseBody)) {
                    String token = extractToken(responseBody);
                    MeeshoPanelSession session = new MeeshoPanelSession(
                            email.trim(),
                            token,
                            cookieHeader(cookies),
                            Instant.now().plus(Duration.ofHours(6)),
                            Map.of("loginPath", path),
                            true);
                    return AuthResult.ok(session, "Meesho panel login verified");
                }
                if (code == 404 || code == 405 || code >= 500) {
                    lastError = "Meesho login route unavailable (" + code + ") at " + path;
                    continue;
                }
                if (StringUtils.hasText(responseBody) && responseBody.toLowerCase().contains("invalid")) {
                    return AuthResult.failed("Meesho rejected email/password. Check Supplier Panel login.");
                }
                lastError = "Unexpected Meesho login response HTTP " + code;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return AuthResult.failed("Meesho login interrupted");
            } catch (Exception ex) {
                lastError = "Meesho login network error: " + ex.getClass().getSimpleName();
                log.warn("Meesho panel login probe failed at {}: {}", path, ex.toString());
            }
        }
        if (liveAuthRequired) {
            return AuthResult.failed(lastError);
        }
        return AuthResult.ok(
                MeeshoPanelSession.unverified(email.trim()),
                "Credentials accepted from property file. Live Meesho session pending (" + lastError + ").");
    }

    @Override
    public List<MeeshoRemoteOrder> fetchPendingOrders(MeeshoPanelSession session) {
        if (session == null) {
            return List.of();
        }
        HttpClient http = client(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        for (String path : ORDER_PATHS) {
            try {
                HttpResponse<String> response = get(http, path, session);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
                JsonNode list = root.path("data");
                if (!list.isArray()) {
                    list = root.path("orders");
                }
                if (!list.isArray()) {
                    list = root.path("payload");
                }
                List<MeeshoRemoteOrder> out = new ArrayList<>();
                if (list.isArray()) {
                    for (JsonNode n : list) {
                        out.add(new MeeshoRemoteOrder(
                                first(text(n, "orderId"), text(n, "id"), text(n, "order_id")),
                                first(text(n, "subOrderId"), text(n, "sub_order_id"), ""),
                                first(text(n, "status"), "PENDING"),
                                first(text(n, "sku"), text(n, "supplierSku")),
                                Math.max(1, n.path("quantity").asInt(1)),
                                text(n, "buyerName"),
                                text(n, "city"),
                                text(n, "awb"),
                                Instant.now(),
                                objectMapper.convertValue(n, Map.class)));
                    }
                }
                log.info("Meesho pulled {} orders via {}", out.size(), path);
                return out;
            } catch (Exception ex) {
                log.warn("Meesho order pull {} failed: {}", path, ex.toString());
            }
        }
        log.info("Meesho order routes not available for {}; returning empty", session.email());
        return List.of();
    }

    @Override
    public int pushStock(MeeshoPanelSession session, List<MeeshoStockUpdate> updates) {
        if (session == null || updates == null || updates.isEmpty()) {
            return 0;
        }
        HttpClient http = client(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        int ok = 0;
        for (MeeshoStockUpdate u : updates) {
            ObjectNode body = objectMapper.createObjectNode()
                    .put("sku", u.sku())
                    .put("inventory", Math.max(0, u.availableQty()))
                    .put("quantity", Math.max(0, u.availableQty()));
            boolean pushed = false;
            for (String path : STOCK_PATHS) {
                try {
                    HttpResponse<String> response = post(http, path, body.toString(), session);
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        ok++;
                        pushed = true;
                        break;
                    }
                } catch (Exception ex) {
                    log.warn("Meesho stock {} failed: {}", path, ex.toString());
                }
            }
            if (!pushed) {
                log.debug("Meesho stock route pending for sku={}", u.sku());
            }
        }
        return ok;
    }

    @Override
    public List<MeeshoLabelResult> fetchLabels(MeeshoPanelSession session, List<String> externalOrderIds) {
        List<MeeshoLabelResult> results = new ArrayList<>();
        if (session == null || externalOrderIds == null) {
            return results;
        }
        HttpClient http = client(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        for (String orderId : externalOrderIds) {
            MeeshoLabelResult found = null;
            for (String path : LABEL_PATHS) {
                try {
                    ObjectNode body = objectMapper.createObjectNode().put("orderId", orderId);
                    HttpResponse<String> response = post(http, path, body.toString(), session);
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
                        String url = first(text(root, "labelUrl"), text(root.path("data"), "labelUrl"));
                        String awb = first(text(root, "awb"), text(root.path("data"), "awb"));
                        found = new MeeshoLabelResult(orderId, awb, url,
                                StringUtils.hasText(url) ? "READY" : "PENDING", null);
                        break;
                    }
                } catch (Exception ex) {
                    log.warn("Meesho label {} failed: {}", path, ex.toString());
                }
            }
            results.add(found != null
                    ? found
                    : new MeeshoLabelResult(orderId, null, null, "PENDING", "Meesho label route pending mapping"));
        }
        return results;
    }

    private HttpClient client(CookieManager cookies) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private HttpResponse<String> post(HttpClient http, String path, String body, MeeshoPanelSession session)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json, text/plain, */*")
                .header("Content-Type", "application/json")
                .header("Origin", baseUrl)
                .header("User-Agent", "CatalogStudio-OMS/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        applyAuth(b, session);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient http, String path, MeeshoPanelSession session) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .header("User-Agent", "CatalogStudio-OMS/1.0")
                .GET();
        applyAuth(b, session);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void applyAuth(HttpRequest.Builder b, MeeshoPanelSession session) {
        if (session == null) {
            return;
        }
        if (StringUtils.hasText(session.accessToken())) {
            b.header("Authorization", "Bearer " + session.accessToken());
        }
        if (StringUtils.hasText(session.cookieHeader())) {
            b.header("Cookie", session.cookieHeader());
        }
    }

    private boolean looksAuthenticated(String body) {
        if (!StringUtils.hasText(body)) {
            return false;
        }
        String lower = body.toLowerCase();
        if (lower.contains("\"token\"")
                || lower.contains("access_token")
                || lower.contains("\"success\":true")
                || lower.contains("\"status\":\"success\"")) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.has("token")
                    || root.path("data").has("token")
                    || root.path("data").has("accessToken")
                    || root.path("success").asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractToken(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String token = text(root, "token");
            if (token == null) {
                token = text(root.path("data"), "token");
            }
            if (token == null) {
                token = text(root.path("data"), "accessToken");
            }
            return token;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static String first(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    private static String cookieHeader(CookieManager cookies) {
        try {
            List<String> parts = cookies.getCookieStore().getCookies().stream()
                    .map(c -> c.getName() + "=" + c.getValue())
                    .toList();
            return parts.isEmpty() ? null : String.join("; ", parts);
        } catch (Exception ex) {
            return null;
        }
    }
}
