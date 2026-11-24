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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HttpAiAutofillClient implements AiAutofillClient {

    private final SupplierProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public HttpAiAutofillClient(SupplierProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AutofillAnalysis analyze(List<ImagePayload> images) {
        try {
            String boundary = "----CS" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = buildMultipart(boundary, images);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.platform().baseUrl().replaceAll("/$", "")
                            + "/internal/v1/ai/wholesale-autofill"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-Internal-Api-Key", properties.platform().internalApiKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("AI autofill failed: HTTP {} body={}", response.statusCode(), response.body());
                throw ApiException.unavailable("AI autofill service unavailable");
            }
            ApiResponse<AutofillAnalysis> parsed = objectMapper.readValue(
                    response.body(), new TypeReference<ApiResponse<AutofillAnalysis>>() {});
            if (parsed.data() == null) {
                throw ApiException.unavailable("AI autofill returned empty result");
            }
            return parsed.data();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("AI autofill failed: {}", ex.toString());
            throw ApiException.unavailable("AI autofill service unavailable");
        }
    }

    private static byte[] buildMultipart(String boundary, List<ImagePayload> images) {
        List<byte[]> parts = new ArrayList<>();
        for (ImagePayload image : images) {
            String filename = image.filename() == null ? "image.jpg" : image.filename();
            String contentType = image.contentType() == null ? "application/octet-stream" : image.contentType();
            String header = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"images\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n";
            parts.add(header.getBytes(StandardCharsets.UTF_8));
            parts.add(image.bytes());
            parts.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        int size = parts.stream().mapToInt(p -> p.length).sum();
        byte[] out = new byte[size];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
