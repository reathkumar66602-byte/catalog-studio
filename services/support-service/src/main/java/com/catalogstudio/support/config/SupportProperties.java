package com.catalogstudio.support.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "catalogstudio")
public record SupportProperties(
        Jwt jwt,
        Cors cors,
        Storage storage,
        Upload upload,
        @DefaultValue Platform platform,
        @DefaultValue Support support
) {
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Storage(
            @DefaultValue("local") String provider,
            String localPath
    ) {}

    public record Upload(
            @DefaultValue("10485760") long maxFileBytes,
            @DefaultValue List<String> allowedContentTypes
    ) {
        public Upload {
            if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
                allowedContentTypes = List.of(
                        "image/jpeg",
                        "image/jpg",
                        "image/png",
                        "image/webp",
                        "application/pdf",
                        "text/plain",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            }
        }
    }

    public record Platform(
            @DefaultValue("http://localhost:8082") String baseUrl,
            @DefaultValue("catalog-studio-internal") String internalApiKey
    ) {}

    public record Support(
            @DefaultValue("30") int rateLimitPerMinute,
            @DefaultValue("20") int chatRateLimitPerMinute,
            @DefaultValue("10") int ticketCreateRateLimitPerMinute,
            @DefaultValue Ai ai,
            @DefaultValue Sla sla
    ) {
        public record Ai(
                @DefaultValue("mock") String provider,
                @DefaultValue("") String apiKey,
                @DefaultValue("gpt-4o-mini") String model,
                @DefaultValue("gemini-2.0-flash") String geminiModel,
                @DefaultValue("25") int timeoutSeconds
        ) {
            public String providerKey() {
                return provider == null ? "mock" : provider.trim().toLowerCase();
            }
        }

        public record Sla(@DefaultValue("60000") long checkIntervalMs) {}
    }
}
