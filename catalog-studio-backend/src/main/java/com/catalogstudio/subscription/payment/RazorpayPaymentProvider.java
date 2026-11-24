package com.catalogstudio.subscription.payment;

import org.springframework.stereotype.Component;

/** Placeholder — not used while Cashfree is the live gateway. */
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
    public CheckoutSession createCheckout(PaymentCheckoutCommand command) {
        String plan = command == null || command.planName() == null ? "" : command.planName();
        Long userId = command == null ? 0L : command.userId();
        return new CheckoutSession("RAZORPAY", "/subscription/pay?plan=" + plan, "razorpay-pending-" + userId);
    }
}
