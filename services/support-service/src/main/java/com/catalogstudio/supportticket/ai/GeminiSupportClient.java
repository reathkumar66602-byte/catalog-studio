package com.catalogstudio.supportticket.ai;

import com.catalogstudio.support.config.SupportProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class GeminiSupportClient implements SupportAiClient {

    private final SupportProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiSupportClient(SupportProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public AiReply reply(String locale, List<ChatTurn> history, String userMessage, String kbContext) {
        String apiKey = properties.support().ai().apiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Gemini API key not configured");
        }
        try {
            String model = properties.support().ai().geminiModel();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent?key=" + apiKey;

            List<Map<String, Object>> contents = new ArrayList<>();
            if (history != null) {
                for (ChatTurn turn : history) {
                    String role = "assistant".equalsIgnoreCase(turn.role()) ? "model" : "user";
                    contents.add(Map.of(
                            "role", role,
                            "parts", List.of(Map.of("text", turn.content()))));
                }
            }
            contents.add(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", userMessage))));

            Map<String, Object> body = new HashMap<>();
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", SupportAiPrompts.system(locale)
                            + "\\n\\nKnowledge context:\\n" + (kbContext == null ? "" : kbContext)))));
            body.put("contents", contents);
            body.put("generationConfig", Map.of("temperature", 0.2));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(properties.support().ai().timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Gemini support reply failed: HTTP {}", response.statusCode());
                throw new IllegalStateException("Gemini request failed");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (!StringUtils.hasText(content)) {
                return AiReply.escalate(SupportAiPrompts.escalateMessage(locale));
            }
            boolean suggest = content.toLowerCase().contains("support ticket")
                    || content.toLowerCase().contains("create a ticket");
            boolean confident = !suggest && content.length() > 40;
            return new AiReply(content.trim(), confident, suggest || !confident);
        } catch (Exception ex) {
            log.warn("Gemini support reply error: {}", ex.toString());
            throw new IllegalStateException("Gemini request failed", ex);
        }
    }
}
