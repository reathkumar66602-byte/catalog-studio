package com.catalogstudio.subscription.payment;

import com.catalogstudio.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CashfreePaymentProvider implements PaymentProvider {

    private final CashfreeProperties properties;
    private final CashfreeClient cashfreeClient;

    @Override
    public String name() {
        return "CASHFREE";
    }

    @Override
    public boolean enabled() {
        return properties.configured();
    }

    @Override
    public CheckoutSession createCheckout(PaymentCheckoutCommand command) {
        if (!enabled()) {
            throw ApiException.badRequest("Cashfree is not configured");
        }
        if (command == null || !StringUtils.hasText(command.linkId())) {
            throw ApiException.badRequest("Payment session is missing");
        }
        if (!StringUtils.hasText(command.customerPhone())) {
            throw ApiException.badRequest("Add a valid 10-digit mobile number in Settings before paying");
        }
        CashfreeClient.OrderCreated created = cashfreeClient.createOrder(command);
        // checkoutUrl carries the payment_session_id for the Cashfree JS SDK (not a plain https link).
        return new CheckoutSession(
                "CASHFREE",
                "cashfree-session:" + created.paymentSessionId(),
                created.orderId(),
                created.cfOrderId()
        );
    }
}
