package com.catalogstudio.subscription.payment;

public interface PaymentProvider {

    String name();

    default boolean enabled() {
        return true;
    }

    CheckoutSession createCheckout(PaymentCheckoutCommand command);

    record CheckoutSession(String provider, String checkoutUrl, String sessionId, String gatewayReference) {
        public CheckoutSession(String provider, String checkoutUrl, String sessionId) {
            this(provider, checkoutUrl, sessionId, sessionId);
        }
    }
}
