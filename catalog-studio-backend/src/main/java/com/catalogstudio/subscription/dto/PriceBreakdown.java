package com.catalogstudio.subscription.dto;

import java.math.BigDecimal;

/** Line-item price breakdown used on quote, checkout, invoices, and admin activation. */
public record PriceBreakdown(
        BigDecimal baseAmount,
        BigDecimal discountAmount,
        BigDecimal serviceCharge,
        BigDecimal gstAmount,
        BigDecimal totalAmount,
        BigDecimal serviceChargePercent,
        BigDecimal gstPercent,
        String promoCode,
        String discountLabel,
        String companyLegalName,
        String companyGstin,
        String parentCompanyName
) {
    public static PriceBreakdown zero() {
        return new PriceBreakdown(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, null);
    }
}
