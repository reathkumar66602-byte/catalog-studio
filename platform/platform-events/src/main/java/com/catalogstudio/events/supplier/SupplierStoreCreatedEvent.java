package com.catalogstudio.events.supplier;

import java.time.Instant;
import java.util.UUID;

public record SupplierStoreCreatedEvent(
        UUID storeId,
        Long ownerUserId,
        String storeName,
        String slug,
        String gstin,
        Instant occurredAt
) {}
