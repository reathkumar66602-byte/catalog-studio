package com.catalogstudio.subscription.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "catalogstudio.cashfree")
public record CashfreeProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("PROD") String env,
        @DefaultValue("") String appId,
        @DefaultValue("") String secretKey,
        @DefaultValue("2023-08-01") String apiVersion,
        @DefaultValue("https://api.cashfree.com/pg") String baseUrl,
        @DefaultValue("https://sandbox.cashfree.com/pg") String sandboxBaseUrl
) {
    public boolean configured() {
        return enabled
                && appId != null && !appId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    public boolean production() {
        return env == null || !"SANDBOX".equalsIgnoreCase(env.trim());
    }

    public String resolvedBaseUrl() {
        if (!production()) {
            return blankTo(sandboxBaseUrl, "https://sandbox.cashfree.com/pg");
        }
        return blankTo(baseUrl, "https://api.cashfree.com/pg");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().replaceAll("/+$", "");
    }
}
