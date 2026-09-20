package com.catalogstudio.subscription.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.subscription.dto.BillingSettingsRequest;
import com.catalogstudio.subscription.dto.BillingSettingsResponse;
import com.catalogstudio.subscription.service.BillingSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin billing")
public class BillingAdminController {

    private final BillingSettingsService billingSettingsService;

    @GetMapping
    public ApiResponse<BillingSettingsResponse> get() {
        return ApiResponse.ok(billingSettingsService.view());
    }

    @PutMapping
    public ApiResponse<BillingSettingsResponse> update(@Valid @RequestBody BillingSettingsRequest request) {
        return ApiResponse.ok("Billing settings saved", billingSettingsService.update(request));
    }
}
