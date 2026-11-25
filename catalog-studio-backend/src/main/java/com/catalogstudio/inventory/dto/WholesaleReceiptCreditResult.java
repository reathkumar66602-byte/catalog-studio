package com.catalogstudio.inventory.dto;

import java.util.UUID;

public record WholesaleReceiptCreditResult(
        UUID receiptId,
        int creditedUnits
) {}
