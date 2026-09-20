package com.catalogstudio.subscription.payment;

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

    public PaymentProvider active() {
        BillingSettings settings = billingSettingsService.current();
        String requested = StringUtils.hasText(settings.getPaymentProvider())
                ? settings.getPaymentProvider().trim().toUpperCase(Locale.ROOT)
                : "MANUAL";
        return providers.stream()
                .filter(provider -> requested.equalsIgnoreCase(provider.name()) && provider.enabled())
                .findFirst()
                .orElseGet(this::manual);
    }

    public PaymentProvider.CheckoutSession checkout(Long userId, String planName) {
        return active().createCheckout(userId, planName);
    }

    private PaymentProvider manual() {
        return providers.stream()
                .filter(provider -> "MANUAL".equalsIgnoreCase(provider.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Manual payment provider is not registered"));
    }
}
