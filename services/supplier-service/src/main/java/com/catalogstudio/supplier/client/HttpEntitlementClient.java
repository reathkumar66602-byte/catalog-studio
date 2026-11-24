package com.catalogstudio.supplier.client;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.supplier.config.SupplierProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HttpEntitlementClient implements EntitlementClient {

    private final SupplierProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public HttpEntitlementClient(SupplierProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntitlementSnapshot accessOf(Long userId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.platform().baseUrl().replaceAll("/$", "")
                            + "/internal/v1/billing/users/" + userId + "/access"))
                    .timeout(Duration.ofSeconds(8))
                    .header("X-Internal-Api-Key", properties.platform().internalApiKey())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw ApiException.unavailable("Billing entitlement service unavailable");
            }
            ApiResponse<EntitlementSnapshot> body = objectMapper.readValue(
                    response.body(), new TypeReference<ApiResponse<EntitlementSnapshot>>() {});
            if (body.data() == null) {
                return new EntitlementSnapshot(null, false, Map.of(), false);
            }
            return body.data();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Entitlement lookup failed for {}: {}", userId, ex.toString());
            throw ApiException.unavailable("Billing entitlement service unavailable");
        }
    }
}
