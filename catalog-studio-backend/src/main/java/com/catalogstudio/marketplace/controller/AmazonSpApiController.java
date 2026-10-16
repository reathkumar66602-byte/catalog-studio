package com.catalogstudio.marketplace.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.marketplace.dto.AmazonSpApiConnectRequest;
import com.catalogstudio.marketplace.dto.AmazonSpApiConnectionResponse;
import com.catalogstudio.marketplace.service.AmazonSpApiConnectionService;
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
@RequestMapping("/api/v1/marketplaces/amazon/sp-api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Amazon SP-API")
public class AmazonSpApiController {

    private final AmazonSpApiConnectionService amazonSpApiConnectionService;

    @GetMapping
    public ApiResponse<AmazonSpApiConnectionResponse> status() {
        return ApiResponse.ok(amazonSpApiConnectionService.status(SecurityUtils.currentUserId()));
    }

    @PostMapping("/connect")
    public ApiResponse<AmazonSpApiConnectionResponse> connect(@Valid @RequestBody AmazonSpApiConnectRequest request) {
        return ApiResponse.ok(
                "Amazon SP-API credentials saved",
                amazonSpApiConnectionService.connect(SecurityUtils.currentUserId(), request));
    }

    @DeleteMapping
    public ApiResponse<AmazonSpApiConnectionResponse> disconnect() {
        return ApiResponse.ok(
                "Amazon SP-API disconnected",
                amazonSpApiConnectionService.disconnect(SecurityUtils.currentUserId()));
    }
}
