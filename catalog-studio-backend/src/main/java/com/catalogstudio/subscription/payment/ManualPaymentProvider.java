package com.catalogstudio.subscription.payment;

import org.springframework.stereotype.Component;

@Component
public class ManualPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "MANUAL";
    }

    @Override
    public CheckoutSession createCheckout(Long userId, String planName) {
        return new CheckoutSession("MANUAL", "/subscription?plan=" + planName, "manual-" + userId);
    }
}
