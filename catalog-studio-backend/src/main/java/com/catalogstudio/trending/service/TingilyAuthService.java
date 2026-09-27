package com.catalogstudio.trending.service;

import com.catalogstudio.config.CatalogStudioProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Tingily auth for Meesho trending. Uses TINGILY_TOKEN when set; otherwise
 * logs in with TINGILY_EMAIL / TINGILY_PASSWORD and caches the JWT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TingilyAuthService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CachedToken> cached = new AtomicReference<>();

    public String bearerToken() {
        CatalogStudioProperties.Tingily cfg = properties.tingily();
        if (cfg == null) {
            return "";
        }
        if (StringUtils.hasText(cfg.token())) {
            return cfg.token().trim();
        }
        if (!StringUtils.hasText(cfg.email()) || !StringUtils.hasText(cfg.password())) {
            return "";
        }
        CachedToken current = cached.get();
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(120))) {
            return current.token();
        }
        synchronized (this) {
            current = cached.get();
            if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(120))) {
                return current.token();
            }
            String token = login(cfg);
            return token == null ? "" : token;
        }
    }

    public String baseUrl() {
        CatalogStudioProperties.Tingily cfg = properties.tingily();
        if (cfg == null || !StringUtils.hasText(cfg.baseUrl())) {
            return "https://engine1.tingily.com";
        }
        return cfg.baseUrl().trim().replaceAll("/+$", "");
    }

    public int fetchLimit() {
        CatalogStudioProperties.Tingily cfg = properties.tingily();
        int limit = cfg == null ? 40 : cfg.fetchLimit();
        return Math.max(TrendingCatalog.PAGE_SIZE, Math.min(limit, 100));
    }

    private String login(CatalogStudioProperties.Tingily cfg) {
        try {
            String body = objectMapper.createObjectNode()
                    .put("email", cfg.email().trim())
                    .put("password", cfg.password())
                    .toString();
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/login"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "CatalogStudio/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Tingily login HTTP {}", response.statusCode());
                return "";
            }
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            String token = text(root, "token");
            if (!StringUtils.hasText(token)) {
                token = text(root.path("data"), "token");
            }
            if (!StringUtils.hasText(token)) {
                log.warn("Tingily login OK but token missing");
                return "";
            }
            long expiresIn = root.path("tokenExpiresIn").asLong(0);
            Instant expires = expiresIn > 60
                    ? Instant.now().plusSeconds(expiresIn - 60)
                    : Instant.now().plus(Duration.ofHours(20));
            cached.set(new CachedToken(token.trim(), expires));
            log.info("Tingily login OK for {}", cfg.email().trim());
            return token.trim();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception ex) {
            log.warn("Tingily login failed: {}", ex.getClass().getSimpleName());
            return "";
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private record CachedToken(String token, Instant expiresAt) {}
}
