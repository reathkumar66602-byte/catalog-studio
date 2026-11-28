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
public class OpenAiSupportClient implements SupportAiClient {

    private final SupportProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiSupportClient(SupportProperties properties, ObjectMapper objectMapper) {
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
            throw new IllegalStateException("OpenAI API key not configured");
        }
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SupportAiPrompts.system(locale)
                    + "\\n\\nKnowledge context:\\n" + (kbContext == null ? "" : kbContext)));
            if (history != null) {
                for (ChatTurn turn : history) {
                    String role = "assistant".equalsIgnoreCase(turn.role()) ? "assistant" : "user";
                    messages.add(Map.of("role", role, "content", turn.content()));
                }
            }
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> body = new HashMap<>();
            body.put("model", properties.support().ai().model());
            body.put("temperature", 0.2);
            body.put("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(properties.support().ai().timeoutSeconds()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("OpenAI support reply failed: HTTP {}", response.statusCode());
                throw new IllegalStateException("OpenAI request failed");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                return AiReply.escalate(SupportAiPrompts.escalateMessage(locale));
            }
            boolean suggest = content.toLowerCase().contains("support ticket")
                    || content.toLowerCase().contains("create a ticket");
            boolean confident = !suggest && content.length() > 40;
            return new AiReply(content.trim(), confident, suggest || !confident);
        } catch (Exception ex) {
            log.warn("OpenAI support reply error: {}", ex.toString());
            throw new IllegalStateException("OpenAI request failed", ex);
        }
    }
}
