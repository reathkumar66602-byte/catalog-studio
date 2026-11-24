package com.catalogstudio.wholesale.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Paginated invoice history with period totals for buyer or supplier. */
public record BillHistoryResponse(
        String fromDate,
        String toDate,
        String storeName,
        long billCount,
        BigDecimal totalSubtotal,
        BigDecimal totalTax,
        BigDecimal totalAmount,
        PageResponse<BillRow> page
) {
    public record BillRow(
            UUID id,
            String orderNumber,
            String status,
            String source,
            Instant billDate,
            String counterpartyName,
            String counterpartyEmail,
            String counterpartyMobile,
            String storeName,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxableAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal gstPercent,
            BigDecimal grandTotal,
            String billPdfUrl
    ) {}

    public static BillHistoryResponse of(
            String fromDate,
            String toDate,
            String storeName,
            List<BillRow> allForTotals,
            PageResponse<BillRow> page
    ) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (BillRow row : allForTotals) {
            if (row.subtotal() != null) {
                subtotal = subtotal.add(row.subtotal());
            }
            BigDecimal cgst = row.cgstAmount() == null ? BigDecimal.ZERO : row.cgstAmount();
            BigDecimal sgst = row.sgstAmount() == null ? BigDecimal.ZERO : row.sgstAmount();
            tax = tax.add(cgst).add(sgst);
            if (row.grandTotal() != null) {
                total = total.add(row.grandTotal());
            }
        }
        return new BillHistoryResponse(
                fromDate,
                toDate,
                storeName,
                allForTotals.size(),
                subtotal,
                tax,
                total,
                page);
    }
}
