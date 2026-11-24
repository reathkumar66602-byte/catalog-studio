package com.catalogstudio.subscription.payment;

import java.math.BigDecimal;
import java.util.Map;

public record PaymentCheckoutCommand(
        Long userId,
        String planName,
        BigDecimal amount,
        String currency,
        String customerName,
        String customerEmail,
        String customerPhone,
        String linkId,
        String returnUrl,
        String notifyUrl,
        Map<String, String> notes
) {}
