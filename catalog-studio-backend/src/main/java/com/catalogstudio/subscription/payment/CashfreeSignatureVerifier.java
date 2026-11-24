package com.catalogstudio.subscription.payment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CashfreeSignatureVerifier {

    private final CashfreeProperties properties;

    public boolean valid(String timestamp, String rawBody, String signature) {
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(rawBody) || !StringUtils.hasText(signature)) {
            return false;
        }
        if (!StringUtils.hasText(properties.secretKey())) {
            return false;
        }
        try {
            String expected = sign(timestamp.trim(), rawBody, properties.secretKey().trim());
            return constantTimeEquals(expected, signature.trim());
        } catch (Exception ex) {
            return false;
        }
    }

    public static String sign(String timestamp, String rawBody, String secret) throws Exception {
        String payload = timestamp + rawBody;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
