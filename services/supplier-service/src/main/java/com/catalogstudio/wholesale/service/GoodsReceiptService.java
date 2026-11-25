package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.supplier.client.InventoryCreditClient;
import com.catalogstudio.wholesale.dto.ConfirmGoodsReceiptRequest;
import com.catalogstudio.wholesale.dto.GoodsReceiptResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Persists the receipt, then asks the platform inventory service to credit accepted units.
 * The HTTP call stays outside the database transaction. Repeating a receipt reuses the same line ids.
 */
@Service
@RequiredArgsConstructor
public class GoodsReceiptService {

    private final GoodsReceiptTx tx;
    private final InventoryCreditClient inventoryCreditClient;

    public GoodsReceiptResponse get(Long buyerUserId, UUID orderId) {
        return tx.get(buyerUserId, orderId);
    }

    public GoodsReceiptResponse confirm(Long buyerUserId, UUID orderId, ConfirmGoodsReceiptRequest request) {
        GoodsReceiptResponse prepared = tx.prepare(buyerUserId, orderId, request);
        if ("CONFIRMED".equals(prepared.status())) {
            return prepared;
        }
        try {
            inventoryCreditClient.credit(tx.creditPayload(prepared.id()));
        } catch (RuntimeException ex) {
            String message = ex instanceof ApiException api ? api.getMessage() : "Retailer inventory was not updated";
            tx.markFailed(prepared.id(), message);
            if (ex instanceof ApiException api) {
                throw api;
            }
            throw ApiException.unavailable("Retailer inventory was not updated");
        }
        return tx.markConfirmed(prepared.id());
    }
}
