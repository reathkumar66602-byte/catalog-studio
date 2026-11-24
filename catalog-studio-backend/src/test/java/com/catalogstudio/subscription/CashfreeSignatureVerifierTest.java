package com.catalogstudio.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.subscription.payment.CashfreeProperties;
import com.catalogstudio.subscription.payment.CashfreeSignatureVerifier;
import org.junit.jupiter.api.Test;

class CashfreeSignatureVerifierTest {

    @Test
    void acceptsValidSignature() throws Exception {
        String secret = "cfsk_test_secret";
        String timestamp = "1617695238078";
        String body = "{\"type\":\"PAYMENT_LINK_EVENT\",\"data\":{\"link_status\":\"PAID\"}}";
        String signature = CashfreeSignatureVerifier.sign(timestamp, body, secret);

        CashfreeSignatureVerifier verifier = new CashfreeSignatureVerifier(
                new CashfreeProperties(true, "PROD", "app", secret, "2023-08-01",
                        "https://api.cashfree.com/pg", "https://sandbox.cashfree.com/pg"));

        assertThat(verifier.valid(timestamp, body, signature)).isTrue();
        assertThat(verifier.valid(timestamp, body + " ", signature)).isFalse();
        assertThat(verifier.valid(timestamp, body, "bad")).isFalse();
    }
}
