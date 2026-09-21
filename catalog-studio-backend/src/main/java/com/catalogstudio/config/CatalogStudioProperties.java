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
        @DefaultValue Billing billing
) {
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Storage(String provider, String localPath, String publicBaseUrl) {}

    public record Ai(String provider, String apiKey, String model, int timeoutSeconds, String promptPath) {}

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
}
