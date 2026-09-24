package com.catalogstudio.trending.service;

import com.catalogstudio.common.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MarketplaceHttp {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceHttp.class);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private MarketplaceHttp() {}

    static String get(String marketplace, String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/json")
                .header("Accept-Language", "en-IN,en;q=0.9")
                .GET()
                .build();
        return send(marketplace, request);
    }

    static String postJson(String marketplace, String url, String json, String origin, String referer, String extraHeaderName, String extraHeaderValue) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Accept-Language", "en-IN,en;q=0.9")
                .header("Origin", origin)
                .header("Referer", referer)
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (extraHeaderName != null && extraHeaderValue != null) {
            builder.header(extraHeaderName, extraHeaderValue);
        }
        return send(marketplace, builder.build());
    }

    private static String send(String marketplace, HttpRequest request) {
        try {
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String body = decode(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("{} trending lookup returned HTTP {}", marketplace, response.statusCode());
                throw ApiException.unavailable(marketplaceLabel(marketplace) + " trending is temporarily unavailable. Your saved products still open without a new lookup.");
            }
            return body;
        } catch (ApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable(marketplaceLabel(marketplace) + " trending is temporarily unavailable. Your saved products still open without a new lookup.");
        } catch (Exception ex) {
            log.warn("{} trending lookup failed: {}", marketplace, ex.getClass().getSimpleName());
            throw ApiException.unavailable(marketplaceLabel(marketplace) + " trending is temporarily unavailable. Your saved products still open without a new lookup.");
        }
    }

    private static String decode(byte[] body) throws Exception {
        if (body == null || body.length == 0) {
            return "";
        }
        byte[] raw = body;
        if (body.length > 2 && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
                raw = gzip.readAllBytes();
            }
        }
        return new String(raw, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String marketplaceLabel(String marketplace) {
        if (marketplace == null || marketplace.isBlank()) {
            return "Marketplace";
        }
        String lower = marketplace.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
