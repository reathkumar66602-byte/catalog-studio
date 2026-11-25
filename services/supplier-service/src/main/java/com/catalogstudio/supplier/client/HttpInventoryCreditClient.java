package com.catalogstudio.supplier.client;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.supplier.client.InventoryCreditClient.InventoryCreditRequest;
import com.catalogstudio.supplier.config.SupplierProperties;
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
public class HttpInventoryCreditClient implements InventoryCreditClient {

    private final SupplierProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public HttpInventoryCreditClient(SupplierProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void credit(InventoryCreditRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.platform().baseUrl().replaceAll("/$", "")
                            + "/internal/v1/inventory/wholesale-receipts"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Internal-Api-Key", properties.platform().internalApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Inventory credit failed: HTTP {}", response.statusCode());
                throw statusException(response.statusCode(), response.body());
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Inventory credit failed: {}", ex.toString());
            throw ApiException.unavailable("Retailer inventory was not updated");
        }
    }

    private ApiException statusException(int status, String body) {
        String message = "Retailer inventory was not updated";
        try {
            ApiResponse<?> parsed = objectMapper.readValue(body, ApiResponse.class);
            if (parsed.message() != null && !parsed.message().isBlank()) {
                message = parsed.message();
            }
        } catch (Exception ignored) {
            message = "Retailer inventory was not updated";
        }
        if (status >= 500) {
            return ApiException.unavailable(message);
        }
        return ApiException.badRequest(message);
    }
}
