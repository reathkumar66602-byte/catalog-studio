package com.catalogstudio.subscription.dto;

import java.math.BigDecimal;

public record PaymentCheckoutResponse(
        String provider,
        String checkoutUrl,
        String sessionId,
        String plan,
        BigDecimal amount,
        String billingCycle,
        String registeredEmail,
        String upiId,
        String payeeName,
        String qrImageUrl,
        String qrImageDataUrl,
        String whatsappNumber,
        String whatsappUrl,
        String whatsappMessage,
        String notice,
        String instructions
) {}
