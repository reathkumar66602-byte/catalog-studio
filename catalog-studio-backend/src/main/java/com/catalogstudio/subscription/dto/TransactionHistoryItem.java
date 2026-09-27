package com.catalogstudio.subscription.dto;

import com.catalogstudio.subscription.entity.PaymentTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionHistoryItem(
        UUID id,
        String reference,
        String type,
        String status,
        String plan,
        BigDecimal amount,
        String currency,
        String provider,
        String billingCycle,
        String notes,
        Instant createdAt,
        BigDecimal baseAmount,
        BigDecimal serviceCharge,
        BigDecimal gstAmount,
        BigDecimal discountAmount,
        String promoCode,
        String companyGstin,
        String invoiceNumber,
        boolean billAvailable
) {
    public static TransactionHistoryItem from(PaymentTransaction row) {
        boolean bill = row.getType() == PaymentTransaction.TransactionType.ACTIVATION
                || row.getType() == PaymentTransaction.TransactionType.CHECKOUT
                || row.getType() == PaymentTransaction.TransactionType.PAYMENT_SENT
                || (row.getAmount() != null && row.getAmount().signum() > 0);
        return new TransactionHistoryItem(
                row.getUuid(),
                row.getReference(),
                row.getType().name(),
                row.getStatus().name(),
                row.getPlanName(),
                row.getAmount(),
                row.getCurrency(),
                row.getProvider(),
                row.getBillingCycle(),
                row.getNotes(),
                row.getCreatedAt(),
                row.getBaseAmount(),
                row.getServiceCharge(),
                row.getGstAmount(),
                row.getDiscountAmount(),
                row.getPromoCode(),
                row.getCompanyGstin(),
                row.getInvoiceNumber(),
                bill
        );
    }
}
