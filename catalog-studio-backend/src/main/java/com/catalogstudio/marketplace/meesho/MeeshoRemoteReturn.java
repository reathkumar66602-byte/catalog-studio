package com.catalogstudio.marketplace.meesho;

import java.time.Instant;
import java.util.Map;

/** Meesho return / RTO row mirrored into the Order Manager. */
public record MeeshoRemoteReturn(
        String externalOrderId,
        String externalSubOrderId,
        String sku,
        int quantity,
        String returnStatus,
        Instant updatedAt,
        Map<String, Object> raw
) {}
