package com.catalogstudio.marketplace.dto;

import java.time.Instant;
import java.util.UUID;

public record MeeshoSyncResultResponse(
        UUID runId,
        String syncType,
        String status,
        int itemsOk,
        int itemsFailed,
        String message,
        Instant startedAt,
        Instant finishedAt
) {}
