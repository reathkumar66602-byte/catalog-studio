package com.catalogstudio.campaign.service;

import com.catalogstudio.config.CatalogStudioProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppOutboundService {

    private final CatalogStudioProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public boolean configured() {
        CatalogStudioProperties.Whatsapp wa = properties.whatsapp();
        return wa != null
                && wa.sendEnabled()
                && StringUtils.hasText(wa.apiUrl())
                && StringUtils.hasText(wa.apiToken());
    }

    public String capabilityMessage() {
        if (configured()) {
            return "WhatsApp delivery is on.";
        }
        return "";
    }

    /**
     * Sends a WhatsApp message when configured. Returns null on success, or a skip/error reason.
     * <p>
     * Provider modes (via {@code catalogstudio.whatsapp.provider}):
     * <ul>
     *   <li>{@code bearer} — POST {@code {"to","body"}} with {@code Authorization: Bearer …}</li>
     *   <li>{@code zoho} — Zoho CPaaS WhatsApp API (template + merge_info; Zoho-enczapikey)</li>
     * </ul>
     */
    public String sendText(String mobile, String body) {
        if (!configured()) {
            return "WhatsApp API not configured";
        }
        String digits = digitsOnly(mobile);
        if (digits.length() < 10) {
            return "User has no valid mobile number";
        }
        CatalogStudioProperties.Whatsapp wa = properties.whatsapp();
        String provider = wa.provider() == null ? "bearer" : wa.provider().trim().toLowerCase();
        try {
            if ("zoho".equals(provider)) {
                return sendZoho(wa, digits, body);
            }
            return sendBearer(wa, digits, body);
        } catch (Exception ex) {
            log.warn("WhatsApp send failed to {}: {}", mobile, ex.toString());
            return "WhatsApp send failed";
        }
    }

    private String sendBearer(CatalogStudioProperties.Whatsapp wa, String digits, String body)
            throws Exception {
        String json = "{\"to\":\"" + digits + "\",\"body\":" + toJsonString(body) + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(wa.apiUrl().trim()))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + wa.apiToken().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            return "WhatsApp provider returned HTTP " + response.statusCode()
                    + ": " + truncate(response.body());
        }
        return null;
    }

    private String sendZoho(CatalogStudioProperties.Whatsapp wa, String digits, String body)
            throws Exception {
        if (!StringUtils.hasText(wa.fromNumber())) {
            return "WhatsApp from number not configured";
        }
        if (!StringUtils.hasText(wa.templateAlias()) && !StringUtils.hasText(wa.templateKey())) {
            return "WhatsApp Zoho template alias/key not configured";
        }
        String to = digits.startsWith("+") ? digits : ("+" + digits);
        String from = wa.fromNumber().trim();
        if (!from.startsWith("+")) {
            from = "+" + from;
        }
        StringBuilder json = new StringBuilder(256);
        json.append("{\"from\":").append(toJsonString(from))
                .append(",\"to\":").append(toJsonString(to));
        if (StringUtils.hasText(wa.templateKey())) {
            json.append(",\"template_key\":").append(toJsonString(wa.templateKey().trim()));
        } else {
            json.append(",\"template_alias\":").append(toJsonString(wa.templateAlias().trim()));
        }
        // Campaign body is rendered text; map into a single merge field expected by the Meta template.
        String mergeField = StringUtils.hasText(wa.mergeBodyField()) ? wa.mergeBodyField().trim() : "body";
        json.append(",\"merge_info\":{")
                .append(toJsonString(mergeField)).append(':').append(toJsonString(body))
                .append("}}");

        String auth = wa.apiToken().trim();
        if (!auth.regionMatches(true, 0, "Zoho-enczapikey", 0, "Zoho-enczapikey".length())) {
            auth = "Zoho-enczapikey " + auth;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(wa.apiUrl().trim()))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", auth)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.toString().getBytes(StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            String detail = truncate(response.body());
            log.warn("Zoho WhatsApp HTTP {}: {}", response.statusCode(), detail);
            return "WhatsApp provider returned HTTP " + response.statusCode() + ": " + detail;
        }
        return null;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300);
    }

    private static String digitsOnly(String mobile) {
        if (mobile == null) {
            return "";
        }
        return mobile.replaceAll("[^0-9]", "");
    }

    private static String toJsonString(String value) {
        String escaped = value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "\"" + escaped + "\"";
    }
}
