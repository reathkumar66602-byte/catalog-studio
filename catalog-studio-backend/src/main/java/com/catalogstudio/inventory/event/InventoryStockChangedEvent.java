package com.catalogstudio.inventory.event;

import java.util.UUID;

/** Fired after warehouse on-hand changes so marketplace stock sync can push deltas. */
public record InventoryStockChangedEvent(
        Long userId,
        UUID variantId,
        String sku,
        int onHand,
        int reserved,
        int available
) {}
