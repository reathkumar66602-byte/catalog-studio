package com.catalogstudio.campaign.service;

import com.catalogstudio.config.CatalogStudioProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
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
            return "WhatsApp send is configured. Messages go to users who have a mobile number.";
        }
        return "WhatsApp templates are stored, but outbound WhatsApp API is not configured. "
                + "Many users have a mobile field, but Catalog Studio currently only uses WhatsApp "
                + "for payment screenshots (wa.me to support). Enable catalogstudio.whatsapp.send-enabled "
                + "plus api-url and api-token to send campaign WhatsApp messages.";
    }

    /**
     * Sends a text message when configured. Returns null on success, or a skip/error reason.
     */
    public String sendText(String mobile, String body) {
        if (!configured()) {
            return "WhatsApp API not configured";
        }
        String digits = digitsOnly(mobile);
        if (digits.length() < 10) {
            return "User has no valid mobile number";
        }
        try {
            CatalogStudioProperties.Whatsapp wa = properties.whatsapp();
            String json = "{\"to\":\"" + digits + "\",\"body\":"
                    + toJsonString(body) + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(wa.apiUrl().trim()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + wa.apiToken().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return "WhatsApp provider returned HTTP " + response.statusCode();
            }
            return null;
        } catch (Exception ex) {
            log.warn("WhatsApp send failed to {}: {}", mobile, ex.toString());
            return "WhatsApp send failed";
        }
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
