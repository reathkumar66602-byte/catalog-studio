package com.catalogstudio.wholesale.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.wholesale.dto.AcceptOrderRequest;
import com.catalogstudio.wholesale.dto.GstLookupRequest;
import com.catalogstudio.wholesale.dto.GstLookupResponse;
import com.catalogstudio.wholesale.dto.OrderResponse;
import com.catalogstudio.wholesale.dto.PageResponse;
import com.catalogstudio.wholesale.dto.ProductCardResponse;
import com.catalogstudio.wholesale.dto.ProductCreateRequest;
import com.catalogstudio.wholesale.dto.ProductDetailResponse;
import com.catalogstudio.wholesale.dto.ProductImageResponse;
import com.catalogstudio.wholesale.dto.ProductUpdateRequest;
import com.catalogstudio.wholesale.dto.RejectOrderRequest;
import com.catalogstudio.wholesale.dto.StockAdjustRequest;
import com.catalogstudio.wholesale.dto.StoreCreateRequest;
import com.catalogstudio.wholesale.dto.StoreResponse;
import com.catalogstudio.wholesale.dto.StoreUpdateRequest;
import com.catalogstudio.wholesale.dto.VariantCell;
import com.catalogstudio.wholesale.dto.VariantInput;
import com.catalogstudio.wholesale.service.WholesaleOrderService;
import com.catalogstudio.wholesale.service.WholesaleProductService;
import com.catalogstudio.wholesale.service.WholesaleStoreService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/wholesale/supplier")
@RequiredArgsConstructor
@Tag(name = "Wholesale supplier")
public class WholesaleSupplierController {

    private final WholesaleStoreService storeService;
    private final WholesaleProductService productService;
    private final WholesaleOrderService orderService;

    // --- store ----------------------------------------------------------------------------

    /** Returns the supplier's store, or an empty payload when no store exists yet. */
    @GetMapping("/store")
    public ApiResponse<StoreResponse> store() {
        return storeService.getMyStore(SecurityUtils.currentUserId())
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.ok("No store yet", null));
    }

    /**
     * Looks up GSTIN on gstinapi.in and returns the locked store name
     * (trade name if present, otherwise legal name).
     */
    @PostMapping("/gst/lookup")
    public ApiResponse<GstLookupResponse> lookupGst(@Valid @RequestBody GstLookupRequest request) {
        return ApiResponse.ok(
                "GST verified",
                storeService.lookupGst(SecurityUtils.currentUserId(), request));
    }

    @PostMapping("/store")
    public ApiResponse<StoreResponse> createStore(@Valid @RequestBody StoreCreateRequest request) {
        return ApiResponse.ok("Store created and GST verified", storeService.create(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/store")
    public ApiResponse<StoreResponse> updateStore(@Valid @RequestBody StoreUpdateRequest request) {
        return ApiResponse.ok("Store updated", storeService.update(SecurityUtils.currentUserId(), request));
    }

    // --- products -------------------------------------------------------------------------

    @GetMapping("/products")
    public ApiResponse<PageResponse<ProductCardResponse>> products(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        return ApiResponse.ok(productService.listMyProducts(SecurityUtils.currentUserId(), status, page, size));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<ProductDetailResponse> product(@PathVariable UUID id) {
        return ApiResponse.ok(productService.getMyProduct(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/products")
    public ApiResponse<ProductDetailResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return ApiResponse.ok("Product created", productService.create(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/products/{id}")
    public ApiResponse<ProductDetailResponse> updateProduct(
            @PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.ok("Product updated", productService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/products/{id}")
    public ApiResponse<ProductDetailResponse> archiveProduct(@PathVariable UUID id) {
        return ApiResponse.ok("Product archived", productService.archive(SecurityUtils.currentUserId(), id));
    }

    @PostMapping(value = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProductImageResponse> uploadImage(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String colorLabel
    ) {
        return ApiResponse.ok("Image uploaded",
                productService.addImage(SecurityUtils.currentUserId(), id, file, colorLabel));
    }

    @DeleteMapping("/products/{id}/images/{imageId}")
    public ApiResponse<Void> deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        productService.deleteImage(SecurityUtils.currentUserId(), id, imageId);
        return ApiResponse.okMessage("Image removed");
    }

    @PostMapping("/products/{id}/publish")
    public ApiResponse<ProductDetailResponse> publish(@PathVariable UUID id) {
        return ApiResponse.ok("Product published", productService.publish(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/products/{id}/variants")
    public ApiResponse<VariantCell> addVariant(@PathVariable UUID id, @Valid @RequestBody VariantInput request) {
        return ApiResponse.ok("Variant added", productService.addVariant(SecurityUtils.currentUserId(), id, request));
    }

    @PostMapping("/variants/{id}/stock")
    public ApiResponse<VariantCell> adjustStock(@PathVariable UUID id, @Valid @RequestBody StockAdjustRequest request) {
        return ApiResponse.ok("Stock updated", productService.adjustStock(SecurityUtils.currentUserId(), id, request));
    }

    // --- orders ---------------------------------------------------------------------------

    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderResponse>> orders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(orderService.listSupplierOrders(SecurityUtils.currentUserId(), status, page, size));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderResponse> order(@PathVariable UUID id) {
        return ApiResponse.ok(orderService.getSupplierOrder(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/orders/{id}/accept")
    public ApiResponse<OrderResponse> accept(
            @PathVariable UUID id, @Valid @RequestBody(required = false) AcceptOrderRequest request) {
        return ApiResponse.ok("Order accepted", orderService.acceptOrder(SecurityUtils.currentUserId(), id, request));
    }

    @PostMapping("/orders/{id}/reject")
    public ApiResponse<OrderResponse> reject(
            @PathVariable UUID id, @Valid @RequestBody(required = false) RejectOrderRequest request) {
        return ApiResponse.ok("Order rejected", orderService.rejectOrder(SecurityUtils.currentUserId(), id, request));
    }

    @PostMapping("/orders/{id}/fulfill")
    public ApiResponse<OrderResponse> fulfill(@PathVariable UUID id) {
        return ApiResponse.ok("Order marked fulfilled", orderService.fulfillOrder(SecurityUtils.currentUserId(), id));
    }
}