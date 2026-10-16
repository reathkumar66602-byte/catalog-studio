package com.catalogstudio.marketplace.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.marketplace.dto.MeeshoChannelOrderResponse;
import com.catalogstudio.marketplace.dto.MeeshoSyncResultResponse;
import com.catalogstudio.marketplace.dto.OmsPlatformStatusResponse;
import com.catalogstudio.marketplace.service.PlatformOmsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super-admin platform OMS (Base.com-style). Credentials come from property/env files.
 */
@RestController
@RequestMapping("/api/v1/admin/oms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Platform OMS")
public class PlatformOmsController {

    private final PlatformOmsService platformOmsService;

    @GetMapping
    public ApiResponse<OmsPlatformStatusResponse> status() {
        return ApiResponse.ok(platformOmsService.status());
    }

    @GetMapping("/{channel}/orders")
    public ApiResponse<List<MeeshoChannelOrderResponse>> orders(@PathVariable String channel) {
        return ApiResponse.ok(platformOmsService.listOrders(channel));
    }

    @PostMapping("/{channel}/sync/{syncType}")
    public ApiResponse<MeeshoSyncResultResponse> sync(
            @PathVariable String channel, @PathVariable String syncType) {
        return ApiResponse.ok(
                "OMS sync finished",
                platformOmsService.sync(channel, syncType));
    }
}
