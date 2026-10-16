package com.catalogstudio.marketplace.dto;

import java.time.Instant;
import java.util.UUID;

public record MeeshoPanelConnectionResponse(
        UUID id,
        String marketplace,
        String connectionType,
        String status,
        String accountLabel,
        String emailMasked,
        boolean passwordConfigured,
        boolean syncEnabled,
        boolean liveAuthVerified,
        String syncNote,
        Instant lastAuthAt,
        Instant lastOrdersSyncAt,
        Instant lastStockSyncAt,
        Instant lastLabelsSyncAt,
        Instant lastActivityAt,
        long pendingOrders
) {}
