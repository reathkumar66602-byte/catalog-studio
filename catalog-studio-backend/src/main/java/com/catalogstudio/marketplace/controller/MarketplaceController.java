package com.catalogstudio.marketplace.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.marketplace.service.MarketplaceService;
import com.catalogstudio.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marketplaces")
@RequiredArgsConstructor
@Tag(name = "Marketplaces")
public class MarketplaceController {

    private final MarketplaceService marketplaceService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(marketplaceService.catalog(SecurityUtils.currentUserId()));
    }

    @PostMapping("/{marketplace}/configure")
    public ApiResponse<Map<String, Object>> configure(@PathVariable String marketplace) {
        var connection = marketplaceService.configure(SecurityUtils.currentUserId(), marketplace);
        return ApiResponse.ok("Marketplace configured", Map.of(
                "id", connection.getUuid(),
                "marketplace", connection.getMarketplace(),
                "status", connection.getStatus().name(),
                "connectionType", connection.getConnectionType()
        ));
    }

    @GetMapping("/{marketplace}/products/{productId}/listing")
    public ApiResponse<Map<String, Object>> prepare(@PathVariable String marketplace, @PathVariable UUID productId) {
        return ApiResponse.ok(marketplaceService.prepare(SecurityUtils.currentUserId(), productId, marketplace));
    }
}
