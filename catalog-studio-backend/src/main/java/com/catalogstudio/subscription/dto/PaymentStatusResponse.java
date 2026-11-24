package com.catalogstudio.subscription.dto;

import java.math.BigDecimal;

public record PaymentStatusResponse(
        String linkId,
        String provider,
        String status,
        String plan,
        BigDecimal amount,
        boolean accessEntitled,
        String message
) {}
