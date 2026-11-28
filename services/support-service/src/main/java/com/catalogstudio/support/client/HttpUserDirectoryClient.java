package com.catalogstudio.support.client;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.support.config.SupportProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HttpUserDirectoryClient implements UserDirectoryClient {

    private final SupportProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public HttpUserDirectoryClient(SupportProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<UserSnapshot> findById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            HttpRequest request = baseGet("/internal/v1/users/" + userId);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() >= 400) {
                log.warn("User lookup failed for {}: HTTP {}", userId, response.statusCode());
                return Optional.empty();
            }
            ApiResponse<UserSnapshot> body = objectMapper.readValue(
                    response.body(), new TypeReference<ApiResponse<UserSnapshot>>() {});
            return Optional.ofNullable(body.data());
        } catch (Exception ex) {
            log.warn("User lookup failed for {}: {}", userId, ex.toString());
            return Optional.empty();
        }
    }

    @Override
    public Map<Long, UserSnapshot> findByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        String ids = userIds.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
        try {
            HttpRequest request = baseGet("/internal/v1/users?ids=" + ids);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Batch user lookup failed: HTTP {}", response.statusCode());
                return Map.of();
            }
            ApiResponse<List<UserSnapshot>> body = objectMapper.readValue(
                    response.body(), new TypeReference<ApiResponse<List<UserSnapshot>>>() {});
            Map<Long, UserSnapshot> map = new HashMap<>();
            if (body.data() != null) {
                for (UserSnapshot user : body.data()) {
                    map.put(user.id(), user);
                }
            }
            return map;
        } catch (Exception ex) {
            log.warn("Batch user lookup failed: {}", ex.toString());
            return Map.of();
        }
    }

    private HttpRequest baseGet(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(properties.platform().baseUrl().replaceAll("/$", "") + path))
                .timeout(Duration.ofSeconds(8))
                .header("X-Internal-Api-Key", properties.platform().internalApiKey())
                .GET()
                .build();
    }
}
