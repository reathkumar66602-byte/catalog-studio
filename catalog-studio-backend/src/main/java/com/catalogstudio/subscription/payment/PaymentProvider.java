package com.catalogstudio.subscription.payment;

public interface PaymentProvider {

    String name();

    default boolean enabled() {
        return true;
    }

    CheckoutSession createCheckout(Long userId, String planName);

    record CheckoutSession(String provider, String checkoutUrl, String sessionId) {}
}
