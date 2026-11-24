package com.catalogstudio.internal.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.internal.InternalApiSupport;
import com.catalogstudio.internal.dto.InternalEntitlementResponse;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/billing/users")
@RequiredArgsConstructor
public class InternalBillingController {

    private final InternalApiSupport internalApiSupport;
    private final SubscriptionAccessService subscriptionAccessService;
    private final UserRepository userRepository;

    @GetMapping("/{id}/access")
    public ApiResponse<InternalEntitlementResponse> access(@PathVariable Long id, HttpServletRequest request) {
        internalApiSupport.requireInternalKey(request);
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        SubscriptionStatusResponse status = subscriptionAccessService.statusOf(id);
        return ApiResponse.ok(new InternalEntitlementResponse(
                status.plan(),
                status.accessEntitled(),
                status.features(),
                user.isStaff()));
    }
}
