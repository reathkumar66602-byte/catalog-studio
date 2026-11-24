package com.catalogstudio.events.supplier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WholesaleOrderLifecycleEvent(
        String type,
        UUID orderId,
        String orderNumber,
        Long storeOwnerUserId,
        Long buyerUserId,
        String buyerEmail,
        String buyerName,
        String storeName,
        String status,
        BigDecimal grandTotal,
        String billPdfUrl,
        Instant occurredAt
) {
    public static final String PLACED = "PLACED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String FULFILLED = "FULFILLED";
    public static final String BILL_READY = "BILL_READY";
}
