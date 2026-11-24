package com.catalogstudio.subscription.payment;

import org.springframework.stereotype.Component;

/**
 * Legacy fallback. Disabled when Cashfree is configured so checkout never falls back to QR/WhatsApp.
 */
@Component
public class ManualPaymentProvider implements PaymentProvider {

    private final CashfreeProperties cashfreeProperties;

    public ManualPaymentProvider(CashfreeProperties cashfreeProperties) {
        this.cashfreeProperties = cashfreeProperties;
    }

    @Override
    public String name() {
        return "MANUAL";
    }

    @Override
    public boolean enabled() {
        return !cashfreeProperties.configured();
    }

    @Override
    public CheckoutSession createCheckout(PaymentCheckoutCommand command) {
        String plan = command == null || command.planName() == null ? "" : command.planName();
        Long userId = command == null ? 0L : command.userId();
        return new CheckoutSession("MANUAL", "/subscription/pay?plan=" + plan, "manual-" + userId);
    }
}
