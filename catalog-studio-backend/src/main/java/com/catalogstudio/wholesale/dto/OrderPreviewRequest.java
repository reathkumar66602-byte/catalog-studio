package com.catalogstudio.wholesale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Same shape as place-order; used to quote totals before committing stock. */
public record OrderPreviewRequest(
        @NotEmpty @Size(max = 100) @Valid List<Item> items,
        @Size(max = 1000) String note,
        @Size(max = 20) String mobile,
        @Size(max = 40) String couponCode
) {
    public record Item(@NotNull UUID variantId, @Min(1) @Max(100000) int quantity) {}

    public PlaceOrderRequest toPlaceOrderRequest() {
        List<PlaceOrderRequest.Item> placeItems = items.stream()
                .map(i -> new PlaceOrderRequest.Item(i.variantId(), i.quantity()))
                .toList();
        return new PlaceOrderRequest(placeItems, note, mobile, couponCode);
    }
}
