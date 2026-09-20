package com.catalogstudio.site.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.site.dto.ClientPromoRequest;
import com.catalogstudio.site.dto.ClientStoreRequest;
import com.catalogstudio.site.dto.EnquiryStatusRequest;
import com.catalogstudio.site.dto.SitePublicResponse.AdminSiteBundle;
import com.catalogstudio.site.dto.SitePublicResponse.ClientAdminResponse;
import com.catalogstudio.site.dto.SitePublicResponse.EnquiryAdminResponse;
import com.catalogstudio.site.dto.SitePublicResponse.PromoAdminResponse;
import com.catalogstudio.site.dto.SitePublicResponse.SiteSettingsResponse;
import com.catalogstudio.site.dto.SiteSettingsRequest;
import com.catalogstudio.site.service.SiteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/site")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin site")
public class SiteAdminController {

    private final SiteService siteService;

    @GetMapping
    public ApiResponse<AdminSiteBundle> bundle() {
        return ApiResponse.ok(siteService.adminBundle());
    }

    @PutMapping
    public ApiResponse<SiteSettingsResponse> updateSettings(@Valid @RequestBody SiteSettingsRequest request) {
        return ApiResponse.ok("Website settings saved", siteService.updateSettings(request));
    }

    @PostMapping("/clients")
    public ApiResponse<ClientAdminResponse> createClient(@Valid @RequestBody ClientStoreRequest request) {
        return ApiResponse.ok("Client saved", siteService.upsertClient(null, request));
    }

    @PutMapping("/clients/{id}")
    public ApiResponse<ClientAdminResponse> updateClient(
            @PathVariable UUID id,
            @Valid @RequestBody ClientStoreRequest request
    ) {
        return ApiResponse.ok("Client saved", siteService.upsertClient(id, request));
    }

    @PostMapping("/promo-codes")
    public ApiResponse<PromoAdminResponse> createPromo(@Valid @RequestBody ClientPromoRequest request) {
        return ApiResponse.ok("Promo code saved", siteService.upsertPromo(null, request));
    }

    @PutMapping("/promo-codes/{id}")
    public ApiResponse<PromoAdminResponse> updatePromo(
            @PathVariable UUID id,
            @Valid @RequestBody ClientPromoRequest request
    ) {
        return ApiResponse.ok("Promo code saved", siteService.upsertPromo(id, request));
    }

    @DeleteMapping("/promo-codes/{id}")
    public ApiResponse<Void> deletePromo(@PathVariable UUID id) {
        siteService.deletePromo(id);
        return ApiResponse.okMessage("Promo code deleted");
    }

    @PostMapping("/enquiries/{id}/status")
    public ApiResponse<EnquiryAdminResponse> enquiryStatus(
            @PathVariable UUID id,
            @Valid @RequestBody EnquiryStatusRequest request
    ) {
        return ApiResponse.ok(siteService.updateEnquiryStatus(id, request));
    }
}
