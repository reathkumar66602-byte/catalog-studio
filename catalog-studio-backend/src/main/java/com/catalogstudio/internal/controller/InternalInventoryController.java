package com.catalogstudio.internal.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.internal.InternalApiSupport;
import com.catalogstudio.inventory.dto.WholesaleReceiptCreditRequest;
import com.catalogstudio.inventory.dto.WholesaleReceiptCreditResult;
import com.catalogstudio.inventory.service.InventoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/inventory")
@RequiredArgsConstructor
public class InternalInventoryController {

    private final InternalApiSupport internalApiSupport;
    private final InventoryService inventoryService;

    @PostMapping("/wholesale-receipts")
    public ApiResponse<WholesaleReceiptCreditResult> creditWholesaleReceipt(
            @Valid @RequestBody WholesaleReceiptCreditRequest request,
            HttpServletRequest httpRequest
    ) {
        internalApiSupport.requireInternalKey(httpRequest);
        return ApiResponse.ok(
                "Accepted quantities credited",
                inventoryService.applyWholesaleReceipt(request));
    }
}
