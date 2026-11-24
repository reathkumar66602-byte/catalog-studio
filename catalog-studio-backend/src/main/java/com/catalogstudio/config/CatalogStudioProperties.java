package com.catalogstudio.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "catalogstudio")
public record CatalogStudioProperties(
        Jwt jwt,
        Cors cors,
        Storage storage,
        Ai ai,
        Upload upload,
        Security security,
        Seed seed,
        @DefaultValue Mail mail,
        @DefaultValue Otp otp,
        @DefaultValue Google google,
        @DefaultValue Billing billing,
        @DefaultValue Tingily tingily,
        @DefaultValue Whatsapp whatsapp,
        @DefaultValue MeeshoPanel meeshoPanel,
        @DefaultValue Oms oms,
        @DefaultValue Wholesale wholesale,
        @DefaultValue CloudCost cloudCost,
        @DefaultValue Modules modules
) {
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {}

    public record Cors(List<String> allowedOrigins) {
        public String publicAppOrigin() {
            if (allowedOrigins != null) {
                String www = null;
                String apex = null;
                String local = null;
                for (String raw : allowedOrigins) {
                    if (raw == null || raw.isBlank()) {
                        continue;
                    }
                    String origin = raw.trim().replaceAll("/+$", "");
                    if (origin.contains("*")) {
                        continue;
                    }
                    String lower = origin.toLowerCase();
                    // Prefer www so Cashfree return_url matches the host sellers actually use
                    // (localStorage auth is origin-scoped; apex vs www must not diverge).
                    if (lower.startsWith("https://www.catalogstudio.in")) {
                        www = origin;
                    } else if (lower.startsWith("https://catalogstudio.in") && apex == null) {
                        apex = origin;
                    } else if (local == null && (lower.contains("localhost") || lower.contains("127.0.0.1"))) {
                        local = origin;
                    }
                }
                if (www != null) {
                    return www;
                }
                if (apex != null) {
                    return apex;
                }
                if (local != null) {
                    return local;
                }
                for (String raw : allowedOrigins) {
                    if (raw != null && !raw.isBlank() && !raw.contains("*")) {
                        return raw.trim().replaceAll("/+$", "");
                    }
                }
            }
            return "https://www.catalogstudio.in";
        }

        /** True when origin is an allowed app origin (scheme + host), used to trust Cashfree return URLs. */
        public boolean isAllowedAppOrigin(String candidate) {
            if (candidate == null || candidate.isBlank()) {
                return false;
            }
            String normalized = candidate.trim().replaceAll("/+$", "");
            if (allowedOrigins == null) {
                return false;
            }
            for (String raw : allowedOrigins) {
                if (raw == null || raw.isBlank() || raw.contains("*")) {
                    continue;
                }
                if (normalized.equalsIgnoreCase(raw.trim().replaceAll("/+$", ""))) {
                    return true;
                }
            }
            return false;
        }
    }

    public record Storage(String provider, String localPath, String publicBaseUrl) {}

    public record Ai(
            String provider,
            String apiKey,
            String model,
            int timeoutSeconds,
            String promptPath,
            @DefaultValue("gpt-image-1") String imageModel,
            /** OpenAI images quality: low | medium | high | auto */
            @DefaultValue("high") String imageQuality,
            /** OpenAI edits input_fidelity for gpt-image models: low | high */
            @DefaultValue("high") String imageInputFidelity
    ) {}

    public record Upload(int maxImages, long maxFileBytes, List<String> allowedContentTypes) {}

    public record Security(int rateLimitPerMinute, int authRateLimitPerMinute) {}

    public record Seed(String adminEmail, String adminPassword, String demoSellerEmail, String demoSellerPassword) {}

    public record Mail(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("zoho") String provider,
            @DefaultValue("smtp.zoho.in") String host,
            @DefaultValue("587") int port,
            String username,
            String password,
            String from,
            @DefaultValue("Catalog Studio") String fromName,
            @DefaultValue("true") boolean startTls,
            @DefaultValue("https://api.zeptomail.in/v1.1/email") String zeptomailApiUrl,
            String zeptomailSendToken,
            @DefaultValue("") String supportInbox
    ) {
        public String fromAddress() {
            if (from != null && !from.isBlank()) {
                return from;
            }
            return username;
        }

        public boolean smtpProvider() {
            String value = provider == null ? "" : provider.trim();
            return value.equalsIgnoreCase("zoho")
                    || value.equalsIgnoreCase("smtp")
                    || value.equalsIgnoreCase("gmail");
        }

        public boolean smtpReady() {
            return smtpProvider()
                    && username != null && !username.isBlank()
                    && password != null && !password.isBlank();
        }

        public String smtpFromAddress() {
            if (smtpProvider() && username != null && !username.isBlank()) {
                return username.trim();
            }
            String address = fromAddress();
            if (address != null && !address.isBlank()) {
                return address.trim();
            }
            return username == null ? null : username.trim();
        }

        public String resolvedHost() {
            String configured = host == null || host.isBlank() ? "smtp.zoho.in" : host.trim();
            if (gmailMailbox() && configured.toLowerCase().contains("zoho")) {
                return "smtp.gmail.com";
            }
            return configured;
        }

        public boolean gmailMailbox() {
            return endsWithGmail(username) || endsWithGmail(smtpFromAddress());
        }

        public String maskedUsername() {
            if (username == null || username.isBlank()) {
                return "(empty)";
            }
            String value = username.trim();
            int at = value.indexOf('@');
            if (at <= 1) {
                return "***";
            }
            return value.charAt(0) + "***" + value.substring(at);
        }

        private static boolean endsWithGmail(String value) {
            return value != null && value.trim().toLowerCase().endsWith("@gmail.com");
        }
    }

    public record Otp(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("6") int length,
            @DefaultValue("10") int ttlMinutes,
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("60") int resendSeconds,
            @DefaultValue("false") boolean logCode
    ) {}

    public record Google(@DefaultValue("") String mapsApiKey) {}

    public record Billing(@DefaultValue("919560111849") String whatsappNumber) {
        public String resolvedWhatsappNumber() {
            String cleaned = whatsappNumber == null ? "" : whatsappNumber.replaceAll("[^0-9]", "");
            if (cleaned.startsWith("00")) {
                cleaned = cleaned.substring(2);
            }
            if (cleaned.length() == 10) {
                return "91" + cleaned;
            }
            return cleaned;
        }
    }

    /** Meesho trending via Tingily Pro account credentials. */
    public record Tingily(
            @DefaultValue("https://engine1.tingily.com") String baseUrl,
            @DefaultValue("") String email,
            @DefaultValue("") String password,
            @DefaultValue("") String token,
            @DefaultValue("40") int fetchLimit
    ) {}

    /**
     * Optional outbound WhatsApp API. When disabled, campaign WhatsApp items are skipped
     * (templates still live in DB). Users may have mobile numbers, but send requires a provider.
     */
    public record Whatsapp(
            @DefaultValue("false") boolean sendEnabled,
            @DefaultValue("") String apiUrl,
            @DefaultValue("") String apiToken
    ) {}

    /** Wholesale marketplace (Live Stock) settings. */
    public record Wholesale(@DefaultValue Gst gst) {
        /**
         * GST verification.
         * <ul>
         *   <li>{@code mock} — local/dev (format only)</li>
         *   <li>{@code gstinapi} — gstinapi.in (recommended for prod)</li>
         *   <li>{@code http} — generic GET/POST registry API</li>
         * </ul>
         */
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
                return notBlank(gstinapiApiKey);
            }

            public String gstinapiLookupUrl(String gstin) {
                String base = notBlank(gstinapiBaseUrl)
                        ? gstinapiBaseUrl.trim().replaceAll("/+$", "")
                        : "https://www.gstinapi.in";
                return base + "/v1/gstin/" + gstin;
            }

            private static boolean notBlank(String value) {
                return value != null && !value.isBlank();
            }
        }
    }

    /** Meesho Supplier Panel OMS connector (email/password, Base-style). */
    public record MeeshoPanel(
            @DefaultValue("https://supplier.meesho.com") String baseUrl,
            /** When true, connect fails unless Meesho login probe verifies. */
            @DefaultValue("false") boolean liveAuthRequired
    ) {}

    /**
     * Platform OMS credentials (Base.com-style). Prefer env / property file — not UI paste.
     * Super admin triggers sync; secrets stay in .env / .env.gcp.
     */
    public record Oms(
            @DefaultValue Flipkart flipkart,
            @DefaultValue Amazon amazon,
            @DefaultValue Meesho meesho
    ) {
        public record Flipkart(
                @DefaultValue("false") boolean enabled,
                @DefaultValue("") String appId,
                @DefaultValue("") String appSecret,
                @DefaultValue("") String locationId
        ) {
            public boolean configured() {
                return notBlank(appId) && notBlank(appSecret);
            }
        }

        public record Amazon(
                @DefaultValue("false") boolean enabled,
                @DefaultValue("") String clientId,
                @DefaultValue("") String clientSecret,
                @DefaultValue("") String refreshToken,
                @DefaultValue("A21TJRUUN4KGV") String marketplaceId,
                /** Selling Partner ID (Seller Central → Account Info). Required for Listings Items path. */
                @DefaultValue("") String sellerId,
                @DefaultValue("true") boolean sandbox
        ) {
            public boolean configured() {
                return notBlank(clientId) && notBlank(clientSecret) && notBlank(refreshToken);
            }
        }

        public record Meesho(
                @DefaultValue("false") boolean enabled,
                @DefaultValue("") String email,
                @DefaultValue("") String password,
                @DefaultValue("https://supplier.meesho.com") String baseUrl,
                @DefaultValue("false") boolean liveAuthRequired
        ) {
            public boolean configured() {
                return notBlank(email) && notBlank(password);
            }
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }

    /**
     * GCP cloud cost dashboard (superadmin). Uses Billing Budgets API + optional BigQuery export.
     */
    public record CloudCost(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("012AA3-8490F8-BDB5E6") String billingAccountId,
            @DefaultValue("INR") String currencyCode,
            @DefaultValue("30000") long annualBudgetInr,
            @DefaultValue("catalog-studio-prod") String quotaProjectId,
            @DefaultValue BigQueryExport bigQuery,
            List<TrackedProject> projects
    ) {
        public record BigQueryExport(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("catalog-studio-prod") String projectId,
                @DefaultValue("gcp_billing_export") String datasetId,
                /** Empty = auto-discover gcp_billing_export_v1_* table. */
                @DefaultValue("") String tableId
        ) {}

        public record TrackedProject(
                String id,
                String name,
                @DefaultValue("") String note
        ) {}
    }

    /**
     * Feature flags for strangler-fig extraction.
     * When wholesaleEnabled=false, supplier-service owns /api/v1/wholesale/**.
     */
    public record Modules(
            @DefaultValue("true") boolean wholesaleEnabled,
            @DefaultValue("catalog-studio-internal") String internalApiKey
    ) {}
}
