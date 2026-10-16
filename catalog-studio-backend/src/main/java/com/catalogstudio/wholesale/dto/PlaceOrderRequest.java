package com.catalogstudio.wholesale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotEmpty @Size(max = 100) @Valid List<Item> items,
        @Size(max = 1000) String note
) {
    public record Item(@NotNull UUID variantId, @Min(1) @Max(100000) int quantity) {}
}