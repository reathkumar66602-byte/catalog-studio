package com.catalogstudio.wholesale.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        String status,
        String source,
        String buyerNote,
        String supplierNote,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal gstPercent,
        BigDecimal grandTotal,
        String couponCode,
        String buyerName,
        String buyerEmail,
        String buyerMobile,
        String buyerGstin,
        String buyerAddress,
        BigDecimal acceptedTotal,
        String currency,
        Instant placedAt,
        Instant acceptedAt,
        Instant rejectedAt,
        String billPdfUrl,
        UUID storeId,
        String storeName,
        List<Item> items,
        /** Only present when status is ACCEPTED/FULFILLED and the viewer is the buyer. */
        SupplierContact supplierContact,
        /** Present for the supplier (store owner) view. */
        BuyerInfo buyer
) {
    public record Item(
            UUID id,
            UUID variantId,
            UUID productId,
            String productTitle,
            String colorLabel,
            String sizeLabel,
            String sku,
            String hsnSac,
            BigDecimal unitPrice,
            BigDecimal acceptedUnitPrice,
            int quantity,
            BigDecimal lineTotal
    ) {}

    public record SupplierContact(String phone, String email) {}

    public record BuyerInfo(String name, String email, String mobile, String gstin, String address) {}
}
