package com.catalogstudio.subscription.payment;

import org.springframework.stereotype.Component;

/**
 * Placeholder for a later Razorpay (and similar hosted) checkout.
 * Scanner + WhatsApp remains the live path until this provider is enabled.
 */
@Component
public class RazorpayPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "RAZORPAY";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public CheckoutSession createCheckout(Long userId, String planName) {
        return new CheckoutSession("RAZORPAY", "/subscription?plan=" + planName, "razorpay-pending-" + userId);
    }
}
