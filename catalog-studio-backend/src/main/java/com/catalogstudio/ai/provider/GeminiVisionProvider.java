package com.catalogstudio.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.catalogstudio.ai.AIProductAnalysisService;
import com.catalogstudio.ai.AIProductAnalysisService.ImagePayload;
import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisResponse;
import com.catalogstudio.ai.AIProvider;
import com.catalogstudio.ai.AiProviderResolver;
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
public class GeminiVisionProvider implements VisionModelClient {

    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;
    private final AnalysisJsonParser parser;

    @Override
    public AIProvider provider() {
        return AIProvider.GEMINI;
    }

    @Override
    public ProductAnalysisResponse analyze(AIProductAnalysisService.ProductAnalysisRequest request, String prompt) {
        if (!StringUtils.hasText(properties.ai().apiKey())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_API_KEY is not configured for Gemini");
        }
        String model = AiProviderResolver.geminiModel(properties.ai().model());
        try {
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("text", prompt));
            List<ImagePayload> images = request.images() == null ? List.of() : request.images();
            for (ImagePayload image : images) {
                String mime = image.contentType() == null || image.contentType().isBlank() ? "image/jpeg" : image.contentType();
                parts.add(Map.of(
                        "inline_data", Map.of(
                                "mime_type", mime,
                                "data", Base64.getEncoder().encodeToString(image.bytes())
                        )
                ));
            }
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", parts)),
                    "generationConfig", Map.of(
                            "temperature", 0.2,
                            "responseMimeType", "application/json"
                    )
            );
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                            + model + ":generateContent?key=" + properties.ai().apiKey().trim()))
                    .timeout(Duration.ofSeconds(properties.ai().timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, geminiError(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
            if (text.isBlank()) {
                throw ApiException.badRequest("AI returned invalid JSON");
            }
            return parser.parse(text, "GEMINI", model);
        } catch (ApiException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI provider timed out");
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI provider request failed");
        }
    }

    private String geminiError(String body) {
        if (body != null && (body.contains("API_KEY_INVALID") || body.contains("PERMISSION_DENIED"))) {
            return "Gemini API key was rejected";
        }
        return "AI provider request failed";
    }
}
