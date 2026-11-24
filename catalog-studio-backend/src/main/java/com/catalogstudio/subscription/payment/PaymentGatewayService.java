package com.catalogstudio.subscription.payment;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.subscription.entity.BillingSettings;
import com.catalogstudio.subscription.service.BillingSettingsService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PaymentGatewayService {

    private final List<PaymentProvider> providers;
    private final BillingSettingsService billingSettingsService;
    private final CashfreeProperties cashfreeProperties;

    public PaymentProvider active() {
        BillingSettings settings = billingSettingsService.current();
        String requested = StringUtils.hasText(settings.getPaymentProvider())
                ? settings.getPaymentProvider().trim().toUpperCase(Locale.ROOT)
                : "CASHFREE";
        // Prefer live Cashfree whenever credentials exist, even if DB still says MANUAL.
        if (cashfreeProperties.configured()
                && ("CASHFREE".equals(requested) || "MANUAL".equals(requested) || !StringUtils.hasText(requested))) {
            return providers.stream()
                    .filter(provider -> "CASHFREE".equalsIgnoreCase(provider.name()) && provider.enabled())
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Cashfree payment gateway is not available"));
        }
        return providers.stream()
                .filter(provider -> requested.equalsIgnoreCase(provider.name()) && provider.enabled())
                .findFirst()
                .orElseGet(this::fallbackManual);
    }

    public PaymentProvider.CheckoutSession checkout(PaymentCheckoutCommand command) {
        return active().createCheckout(command);
    }

    private PaymentProvider fallbackManual() {
        return providers.stream()
                .filter(provider -> "MANUAL".equalsIgnoreCase(provider.name()) && provider.enabled())
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest(
                        "No payment gateway is configured. Set Cashfree credentials on the server."));
    }
}
