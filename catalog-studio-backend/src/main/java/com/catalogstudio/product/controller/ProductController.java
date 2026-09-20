package com.catalogstudio.product.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.product.dto.ProductResponse;
import com.catalogstudio.product.dto.ProductUpdateRequest;
import com.catalogstudio.product.service.ProductService;
import com.catalogstudio.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<Page<ProductResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(productService.search(
                SecurityUtils.currentUserId(),
                q, category, color, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(productService.get(SecurityUtils.currentUserId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.ok("Product saved", productService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        productService.delete(SecurityUtils.currentUserId(), id);
        return ApiResponse.okMessage("Product deleted");
    }
}
