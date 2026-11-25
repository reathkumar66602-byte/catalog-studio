package com.catalogstudio.supplier.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InventoryCreditClient {

    void credit(InventoryCreditRequest request);

    record InventoryCreditRequest(
            Long buyerUserId,
            UUID receiptId,
            List<Line> lines
    ) {
        public record Line(
                UUID lineId,
                String sku,
                String title,
                String colorLabel,
                String sizeLabel,
                int acceptedQty,
                BigDecimal unitCost
        ) {}
    }
}
