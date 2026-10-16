package com.catalogstudio.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InventoryAdjustRequest(
        @NotNull UUID variantId,
        @NotNull @Min(0) Integer onHand,
        String note
) {}
