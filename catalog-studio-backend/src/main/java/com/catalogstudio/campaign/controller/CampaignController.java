package com.catalogstudio.campaign.controller;

import com.catalogstudio.campaign.dto.CampaignRunResponse;
import com.catalogstudio.campaign.dto.CampaignTriggerRequest;
import com.catalogstudio.campaign.dto.NotificationTemplateResponse;
import com.catalogstudio.campaign.service.CampaignService;
import com.catalogstudio.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/campaigns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping("/templates")
    public ApiResponse<List<NotificationTemplateResponse>> templates() {
        return ApiResponse.ok(campaignService.templates());
    }

    @GetMapping("/runs")
    public ApiResponse<List<CampaignRunResponse>> runs() {
        return ApiResponse.ok(campaignService.recentRuns());
    }

    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Object>> capabilities() {
        return ApiResponse.ok(campaignService.capabilities());
    }

    @PostMapping("/trigger")
    public ApiResponse<CampaignRunResponse> trigger(@Valid @RequestBody CampaignTriggerRequest request) {
        return ApiResponse.ok("Campaign queued", campaignService.trigger(request));
    }
}
