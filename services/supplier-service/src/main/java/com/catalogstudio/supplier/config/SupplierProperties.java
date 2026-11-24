package com.catalogstudio.supplier.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "catalogstudio")
public record SupplierProperties(
        Jwt jwt,
        Cors cors,
        Storage storage,
        Upload upload,
        @DefaultValue Platform platform,
        @DefaultValue Wholesale wholesale
) {
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Storage(
            @DefaultValue("local") String provider,
            String localPath,
            String publicBaseUrl
    ) {}

    public record Upload(
            @DefaultValue("10485760") long maxFileBytes,
            @DefaultValue("5") int maxImages,
            @DefaultValue List<String> allowedContentTypes
    ) {
        public Upload {
            if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
                allowedContentTypes = List.of("image/jpeg", "image/jpg", "image/png", "image/webp");
            }
        }
    }

    public record Platform(
            @DefaultValue("http://localhost:8082") String baseUrl,
            @DefaultValue("catalog-studio-internal") String internalApiKey
    ) {}

    public record Wholesale(@DefaultValue Gst gst) {
        public record Gst(
                @DefaultValue("mock") String provider,
                @DefaultValue("") String httpUrl,
                @DefaultValue("") String httpApiKey,
                @DefaultValue("0.72") double nameMatchThreshold,
                @DefaultValue("https://www.gstinapi.in") String gstinapiBaseUrl,
                @DefaultValue("") String gstinapiApiKey
        ) {
            public String providerKey() {
                return provider == null ? "mock" : provider.trim().toLowerCase();
            }

            public boolean mockProvider() {
                return "mock".equals(providerKey()) || providerKey().isBlank();
            }

            public boolean gstinapiProvider() {
                return "gstinapi".equals(providerKey());
            }

            public boolean httpProvider() {
                return "http".equals(providerKey());
            }

            public boolean liveProvider() {
                return gstinapiProvider() || httpProvider();
            }

            public boolean gstinapiConfigured() {
                return gstinapiApiKey != null && !gstinapiApiKey.isBlank();
            }

            public String gstinapiLookupUrl(String gstin) {
                String base = gstinapiBaseUrl != null && !gstinapiBaseUrl.isBlank()
                        ? gstinapiBaseUrl.trim().replaceAll("/+$", "")
                        : "https://www.gstinapi.in";
                return base + "/v1/gstin/" + gstin;
            }
        }
    }
}
