package com.catalogstudio.marketplace.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.marketplace.dto.FlipkartSellerConnectRequest;
import com.catalogstudio.marketplace.dto.SellerConnectionResponse;
import com.catalogstudio.marketplace.service.FlipkartSellerConnectionService;
import com.catalogstudio.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marketplaces/flipkart/seller-api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Flipkart Seller API")
public class FlipkartSellerController {

    private final FlipkartSellerConnectionService flipkartSellerConnectionService;

    @GetMapping
    public ApiResponse<SellerConnectionResponse> status() {
        return ApiResponse.ok(flipkartSellerConnectionService.status(SecurityUtils.currentUserId()));
    }

    @PostMapping("/connect")
    public ApiResponse<SellerConnectionResponse> connect(@Valid @RequestBody FlipkartSellerConnectRequest request) {
        return ApiResponse.ok(
                "Flipkart Seller API credentials saved",
                flipkartSellerConnectionService.connect(SecurityUtils.currentUserId(), request));
    }

    @DeleteMapping
    public ApiResponse<SellerConnectionResponse> disconnect() {
        return ApiResponse.ok(
                "Flipkart Seller API disconnected",
                flipkartSellerConnectionService.disconnect(SecurityUtils.currentUserId()));
    }
}
