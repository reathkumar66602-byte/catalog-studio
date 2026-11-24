package com.catalogstudio.subscription.dto;

import com.catalogstudio.subscription.entity.PaymentTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminPaymentLedgerItem(
        UUID id,
        UUID userId,
        String userEmail,
        String userName,
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
        Instant paidAt,
        BigDecimal baseAmount,
        BigDecimal serviceCharge,
        BigDecimal gstAmount,
        BigDecimal discountAmount,
        String promoCode,
        String companyGstin,
        String invoiceNumber,
        String gatewayOrderId,
        String gatewayPaymentId,
        boolean billAvailable
) {
    public static AdminPaymentLedgerItem from(PaymentTransaction row) {
        boolean bill = row.getType() == PaymentTransaction.TransactionType.ACTIVATION
                || row.getType() == PaymentTransaction.TransactionType.CHECKOUT
                || row.getType() == PaymentTransaction.TransactionType.PAYMENT_SENT
                || (row.getAmount() != null && row.getAmount().signum() > 0);
        return new AdminPaymentLedgerItem(
                row.getUuid(),
                row.getUser() == null ? null : row.getUser().getUuid(),
                row.getUser() == null ? "" : row.getUser().getEmail(),
                row.getUser() == null ? "" : row.getUser().getName(),
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
                row.getPaidAt(),
                row.getBaseAmount(),
                row.getServiceCharge(),
                row.getGstAmount(),
                row.getDiscountAmount(),
                row.getPromoCode(),
                row.getCompanyGstin(),
                row.getInvoiceNumber(),
                row.getGatewayOrderId(),
                row.getGatewayPaymentId(),
                bill
        );
    }
}
