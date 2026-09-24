package com.catalogstudio.shoot.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCatalogImageEditor implements CatalogImageEditor {

    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Override
    public byte[] edit(List<ShootPlan.Ref> images, String prompt, String size) {
        if (!StringUtils.hasText(properties.ai().apiKey())) {
            throw ApiException.unavailable("AI_API_KEY is not configured");
        }
        if (images == null || images.isEmpty()) {
            throw ApiException.badRequest("A garment photo is required");
        }
        try {
            String boundary = "----CatalogStudio" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = multipart(boundary, images, prompt, size);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/images/edits"))
                    .timeout(Duration.ofSeconds(180))
                    .header("Authorization", "Bearer " + properties.ai().apiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw failure(response);
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data").path(0);
            String encoded = data.path("b64_json").asText("");
            if (encoded.isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI did not return an image");
            }
            return Base64.getDecoder().decode(encoded);
        } catch (ApiException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "OpenAI image generation timed out");
        } catch (Exception ex) {
            log.warn("OpenAI image edit failed: {}", ex.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI image generation failed");
        }
    }

    private byte[] multipart(String boundary, List<ShootPlan.Ref> images, String prompt, String size) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String configured = properties.ai().imageModel();
        String model = StringUtils.hasText(configured) ? configured.trim() : "gpt-image-1";
        textPart(out, boundary, "model", model);
        textPart(out, boundary, "prompt", prompt);
        textPart(out, boundary, "n", "1");
        String imageSize = "auto".equals(size) || "1024x1536".equals(size) || "1024x1024".equals(size) || "1536x1024".equals(size)
                ? size
                : "auto";
        textPart(out, boundary, "size", imageSize);
        textPart(out, boundary, "quality", "medium");
        int index = 1;
        String field = images.size() > 1 ? "image[]" : "image";
        for (ShootPlan.Ref image : images) {
            String type = image.contentType() == null ? "image/jpeg" : image.contentType();
            String filename = image.filename() == null ? "garment-" + index + extension(type) : image.filename();
            filePart(out, boundary, field, filename, type, image.bytes());
            index++;
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void textPart(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void filePart(
            ByteArrayOutputStream out,
            String boundary,
            String name,
            String filename,
            String contentType,
            byte[] bytes
    ) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private ApiException failure(HttpResponse<String> response) {
        String errorBody = response.body() == null ? "" : response.body();
        String snippet = errorBody.length() > 500 ? errorBody.substring(0, 500) : errorBody;
        log.warn("OpenAI image edit failed status={} body={}", response.statusCode(), snippet);
        if (errorBody.contains("insufficient_quota") || errorBody.contains("billing_not_active")) {
            return new ApiException(HttpStatus.PAYMENT_REQUIRED, "OpenAI billing or quota is not available for this API key");
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            return new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI API key was rejected");
        }
        if (errorBody.contains("content_policy") || errorBody.contains("moderation_blocked")) {
            return new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI could not create this catalog photo. Try another garment photo.");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI image generation failed");
    }
}
