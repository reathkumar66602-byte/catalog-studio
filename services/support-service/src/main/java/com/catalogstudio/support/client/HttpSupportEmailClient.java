package com.catalogstudio.support.client;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.events.support.SupportEmailNotificationEvent;
import com.catalogstudio.support.config.SupportProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HttpSupportEmailClient implements SupportEmailClient {

    private final SupportProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public HttpSupportEmailClient(SupportProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean send(SupportEmailNotificationEvent event) {
        if (event == null) {
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.platform().baseUrl().replaceAll("/$", "")
                            + "/internal/v1/support/emails"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Api-Key", properties.platform().internalApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Support email HTTP fallback failed for {}: HTTP {}",
                        event.ticketNumber(), response.statusCode());
                return false;
            }
            ApiResponse<Boolean> body = objectMapper.readValue(
                    response.body(), new TypeReference<ApiResponse<Boolean>>() {});
            return body.data() == null || Boolean.TRUE.equals(body.data());
        } catch (Exception ex) {
            log.warn("Support email HTTP fallback failed for {}: {}",
                    event == null ? null : event.ticketNumber(), ex.toString());
            return false;
        }
    }
}
