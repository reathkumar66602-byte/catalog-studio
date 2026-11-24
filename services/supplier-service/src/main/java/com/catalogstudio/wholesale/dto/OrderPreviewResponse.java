package com.catalogstudio.wholesale.dto;

import java.math.BigDecimal;

public record OrderPreviewResponse(
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal gstPercent,
        BigDecimal grandTotal,
        String couponCode,
        String currency
) {}
