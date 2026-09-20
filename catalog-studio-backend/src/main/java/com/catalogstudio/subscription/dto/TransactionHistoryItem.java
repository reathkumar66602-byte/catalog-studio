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
        Instant createdAt
) {
    public static TransactionHistoryItem from(PaymentTransaction row) {
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
                row.getCreatedAt()
        );
    }
}
