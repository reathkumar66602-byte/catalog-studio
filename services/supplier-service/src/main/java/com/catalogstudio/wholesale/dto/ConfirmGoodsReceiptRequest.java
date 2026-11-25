package com.catalogstudio.wholesale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ConfirmGoodsReceiptRequest(
        @Size(max = 1000) String note,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            @NotNull UUID orderItemId,
            @NotNull @Min(0) Integer receivedQty,
            @NotNull @Min(0) Integer damagedQty,
            @NotNull @Min(0) Integer rejectedQty
    ) {}
}
