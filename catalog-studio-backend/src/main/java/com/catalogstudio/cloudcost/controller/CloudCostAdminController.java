package com.catalogstudio.cloudcost.controller;

import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse;
import com.catalogstudio.cloudcost.service.CloudCostService;
import com.catalogstudio.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/cloud-cost")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Admin Cloud Cost")
public class CloudCostAdminController {

    private final CloudCostService cloudCostService;

    @GetMapping
    public ApiResponse<CloudCostDashboardResponse> dashboard() {
        return ApiResponse.ok(cloudCostService.dashboard());
    }
}
