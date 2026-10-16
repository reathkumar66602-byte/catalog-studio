package com.catalogstudio.wholesale.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.security.AuthUser;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.wholesale.dto.CategoryResponse;
import com.catalogstudio.wholesale.dto.PageResponse;
import com.catalogstudio.wholesale.dto.ProductCardResponse;
import com.catalogstudio.wholesale.dto.ProductDetailResponse;
import com.catalogstudio.wholesale.dto.StoreResponse;
import com.catalogstudio.wholesale.service.WholesaleCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Live-stock browse. Gated by SubscriptionAccessFilter like every other /api/v1 route. */
@RestController
@RequestMapping("/api/v1/wholesale")
@RequiredArgsConstructor
@Tag(name = "Wholesale catalog")
public class WholesaleCatalogController {

    private final WholesaleCatalogService catalogService;

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> categories() {
        return ApiResponse.ok(catalogService.categories());
    }

    @GetMapping("/products")
    public ApiResponse<PageResponse<ProductCardResponse>> products(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String store,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        return ApiResponse.ok(catalogService.products(category, store, q, sort, page, size));
    }

    @GetMapping("/products/{idOrSlug}")
    public ApiResponse<ProductDetailResponse> product(@PathVariable String idOrSlug) {
        Long viewer = SecurityUtils.optionalUser().map(AuthUser::id).orElse(null);
        return ApiResponse.ok(catalogService.product(idOrSlug, viewer));
    }

    @GetMapping("/stores")
    public ApiResponse<PageResponse<StoreResponse>> stores(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        return ApiResponse.ok(catalogService.stores(category, page, size));
    }
}