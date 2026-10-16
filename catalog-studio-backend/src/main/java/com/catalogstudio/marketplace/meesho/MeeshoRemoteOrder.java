package com.catalogstudio.marketplace.meesho;

import java.time.Instant;
import java.util.Map;

public record MeeshoRemoteOrder(
        String externalOrderId,
        String externalSubOrderId,
        String status,
        String sku,
        int quantity,
        String buyerName,
        String shippingCity,
        String awb,
        Instant orderedAt,
        Map<String, Object> raw
) {}
