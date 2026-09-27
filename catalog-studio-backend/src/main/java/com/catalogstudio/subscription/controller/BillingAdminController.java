package com.catalogstudio.subscription.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.subscription.dto.BillingPromoCodeRequest;
import com.catalogstudio.subscription.dto.BillingPromoCodeResponse;
import com.catalogstudio.subscription.dto.BillingSettingsRequest;
import com.catalogstudio.subscription.dto.BillingSettingsResponse;
import com.catalogstudio.subscription.service.BillingPromoService;
import com.catalogstudio.subscription.service.BillingSettingsService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin billing")
public class BillingAdminController {

    private final BillingSettingsService billingSettingsService;
    private final BillingPromoService billingPromoService;
    private final UserRepository userRepository;

    @GetMapping
    public ApiResponse<BillingSettingsResponse> get() {
        return ApiResponse.ok(billingSettingsService.view());
    }

    @PutMapping
    public ApiResponse<BillingSettingsResponse> update(@Valid @RequestBody BillingSettingsRequest request) {
        User actor = userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow();
        return ApiResponse.ok(
                "Billing settings saved",
                billingSettingsService.update(request, actor.isSuperAdmin()));
    }

    @GetMapping("/promos")
    public ApiResponse<List<BillingPromoCodeResponse>> promos() {
        return ApiResponse.ok(billingPromoService.listAll());
    }

    @PostMapping("/promos")
    public ApiResponse<BillingPromoCodeResponse> createPromo(@Valid @RequestBody BillingPromoCodeRequest request) {
        return ApiResponse.ok(
                "Promo code created",
                billingPromoService.create(request, SecurityUtils.currentUserId()));
    }

    @PutMapping("/promos/{id}")
    public ApiResponse<BillingPromoCodeResponse> updatePromo(
            @PathVariable UUID id,
            @Valid @RequestBody BillingPromoCodeRequest request
    ) {
        return ApiResponse.ok("Promo code updated", billingPromoService.update(id, request));
    }

    @DeleteMapping("/promos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disablePromo(@PathVariable UUID id) {
        billingPromoService.disable(id);
    }
}
