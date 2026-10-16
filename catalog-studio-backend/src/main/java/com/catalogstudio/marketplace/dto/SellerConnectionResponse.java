package com.catalogstudio.marketplace.dto;

import java.time.Instant;
import java.util.UUID;

public record SellerConnectionResponse(
        UUID id,
        String marketplace,
        String connectionType,
        String status,
        String accountLabel,
        String appIdMasked,
        String locationId,
        boolean secretConfigured,
        String syncNote,
        Instant lastActivityAt
) {}
