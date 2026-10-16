package com.catalogstudio.marketplace.dto;

import java.time.Instant;
import java.util.UUID;

public record MeeshoChannelOrderResponse(
        UUID id,
        String marketplace,
        String externalOrderId,
        String externalSubOrderId,
        String status,
        String sku,
        int quantity,
        String buyerName,
        String shippingCity,
        String awb,
        String labelStatus,
        String labelUrl,
        Instant orderedAt,
        Instant lastSyncedAt
) {}
