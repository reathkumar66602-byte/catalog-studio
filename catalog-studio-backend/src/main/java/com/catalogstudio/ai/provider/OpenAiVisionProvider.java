package com.catalogstudio.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.catalogstudio.ai.AIProductAnalysisService;
import com.catalogstudio.ai.AIProductAnalysisService.ImagePayload;
import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisResponse;
import com.catalogstudio.ai.AIProvider;
import com.catalogstudio.ai.VisionModelClient;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class OpenAiVisionProvider implements VisionModelClient {

    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;
    private final AnalysisJsonParser parser;

    @Override
    public AIProvider provider() {
        return AIProvider.OPENAI;
    }

    @Override
    public ProductAnalysisResponse analyze(AIProductAnalysisService.ProductAnalysisRequest request, String prompt) {
        if (!StringUtils.hasText(properties.ai().apiKey())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_API_KEY is not configured");
        }
        try {
            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of("type", "text", "text", prompt));
            for (ImagePayload image : request.images()) {
                String mime = image.contentType() == null ? "image/jpeg" : image.contentType();
                String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image.bytes());
                content.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", dataUrl)
                ));
            }
            Map<String, Object> body = Map.of(
                    "model", properties.ai().model(),
                    "temperature", 0.2,
                    "messages", List.of(Map.of("role", "user", "content", content))
            );
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(properties.ai().timeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.ai().apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                String errorBody = response.body() == null ? "" : response.body();
                if (errorBody.contains("insufficient_quota") || errorBody.contains("billing_not_active")) {
                    throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "OpenAI billing or quota is not available for this API key");
                }
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI API key was rejected");
                }
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI provider request failed");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("choices").path(0).path("message").path("content").asText();
            if (text.isBlank()) {
                throw ApiException.badRequest("AI returned invalid JSON");
            }
            return parser.parse(text, "OPENAI", properties.ai().model());
        } catch (ApiException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI provider timed out");
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI provider request failed");
        }
    }
}
