package com.catalogstudio.inventory.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.inventory.dto.InventoryAdjustRequest;
import com.catalogstudio.inventory.dto.VariantCreateRequest;
import com.catalogstudio.inventory.dto.VariantResponse;
import com.catalogstudio.inventory.service.InventoryService;
import com.catalogstudio.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/inventory")
    public ApiResponse<List<VariantResponse>> inventory() {
        return ApiResponse.ok(inventoryService.listInventory(SecurityUtils.currentUserId()));
    }

    @GetMapping("/products/{productId}/variants")
    public ApiResponse<List<VariantResponse>> variants(@PathVariable UUID productId) {
        return ApiResponse.ok(inventoryService.listVariantsForProduct(SecurityUtils.currentUserId(), productId));
    }

    @PostMapping("/products/{productId}/variants")
    public ApiResponse<VariantResponse> createVariant(
            @PathVariable UUID productId, @Valid @RequestBody VariantCreateRequest request) {
        return ApiResponse.ok(
                "Variant created",
                inventoryService.createVariant(SecurityUtils.currentUserId(), productId, request));
    }

    @PostMapping("/inventory/adjust")
    public ApiResponse<VariantResponse> adjust(@Valid @RequestBody InventoryAdjustRequest request) {
        return ApiResponse.ok("Stock updated", inventoryService.adjust(SecurityUtils.currentUserId(), request));
    }
}
