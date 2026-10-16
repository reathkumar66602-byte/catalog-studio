package com.catalogstudio.marketplace.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.marketplace.dto.MeeshoChannelOrderResponse;
import com.catalogstudio.marketplace.dto.MeeshoPanelConnectRequest;
import com.catalogstudio.marketplace.dto.MeeshoPanelConnectionResponse;
import com.catalogstudio.marketplace.dto.MeeshoSyncResultResponse;
import com.catalogstudio.marketplace.service.MeeshoPanelConnectionService;
import com.catalogstudio.marketplace.service.MeeshoPanelSyncService;
import com.catalogstudio.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marketplaces/meesho/panel")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Meesho Panel OMS")
public class MeeshoPanelController {

    private final MeeshoPanelConnectionService connectionService;
    private final MeeshoPanelSyncService syncService;

    @GetMapping
    public ApiResponse<MeeshoPanelConnectionResponse> status() {
        return ApiResponse.ok(connectionService.status(SecurityUtils.currentUserId()));
    }

    @PostMapping("/connect")
    public ApiResponse<MeeshoPanelConnectionResponse> connect(@Valid @RequestBody MeeshoPanelConnectRequest request) {
        return ApiResponse.ok(
                "Meesho Supplier Panel connected",
                connectionService.connect(SecurityUtils.currentUserId(), request));
    }

    @DeleteMapping
    public ApiResponse<MeeshoPanelConnectionResponse> disconnect() {
        return ApiResponse.ok(
                "Meesho Supplier Panel disconnected",
                connectionService.disconnect(SecurityUtils.currentUserId()));
    }

    @GetMapping("/orders")
    public ApiResponse<List<MeeshoChannelOrderResponse>> orders() {
        return ApiResponse.ok(syncService.listOrders(SecurityUtils.currentUserId()));
    }

    @GetMapping("/sync-runs")
    public ApiResponse<List<MeeshoSyncResultResponse>> syncRuns() {
        return ApiResponse.ok(syncService.recentRuns(SecurityUtils.currentUserId()));
    }

    @PostMapping("/sync/orders")
    public ApiResponse<MeeshoSyncResultResponse> syncOrders() {
        return ApiResponse.ok("Meesho orders sync finished", syncService.syncOrders(SecurityUtils.currentUserId()));
    }

    @PostMapping("/sync/stock")
    public ApiResponse<MeeshoSyncResultResponse> syncStock() {
        return ApiResponse.ok("Meesho stock sync finished", syncService.syncStock(SecurityUtils.currentUserId()));
    }

    @PostMapping("/sync/labels")
    public ApiResponse<MeeshoSyncResultResponse> syncLabels() {
        return ApiResponse.ok("Meesho labels sync finished", syncService.syncLabels(SecurityUtils.currentUserId()));
    }

    @PostMapping("/sync/full")
    public ApiResponse<MeeshoSyncResultResponse> syncFull() {
        return ApiResponse.ok("Meesho full sync finished", syncService.syncFull(SecurityUtils.currentUserId()));
    }
}
