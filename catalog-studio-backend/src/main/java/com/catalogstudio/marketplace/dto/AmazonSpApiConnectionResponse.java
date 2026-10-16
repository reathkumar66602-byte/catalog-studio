package com.catalogstudio.marketplace.dto;

import java.time.Instant;
import java.util.UUID;

public record AmazonSpApiConnectionResponse(
        UUID id,
        String marketplace,
        String connectionType,
        String status,
        String accountLabel,
        String clientIdMasked,
        String marketplaceId,
        boolean secretConfigured,
        boolean refreshTokenConfigured,
        boolean sandbox,
        String syncNote,
        Instant lastActivityAt
) {}
