package com.catalogstudio.subscription.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.subscription.dto.PaymentCheckoutResponse;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.repository.SubscriptionPlanRepository;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions")
public class SubscriptionController {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionAccessService accessService;

    @GetMapping("/plans")
    public ApiResponse<List<Map<String, Object>>> plans() {
        return ApiResponse.ok(planRepository.findByStatusOrderByPriceAsc("ACTIVE").stream()
                .map(this::toPlan)
                .toList());
    }

    @GetMapping("/current")
    public ApiResponse<SubscriptionStatusResponse> current() {
        return ApiResponse.ok(accessService.statusOf(SecurityUtils.currentUserId()));
    }

    @PostMapping("/plans/{name}/checkout")
    public ApiResponse<PaymentCheckoutResponse> checkout(@PathVariable String name) {
        return ApiResponse.ok(accessService.checkout(SecurityUtils.currentUserId(), name));
    }

    @PostMapping("/plans/{name}/payment-sent")
    public ApiResponse<SubscriptionStatusResponse> paymentSent(@PathVariable String name) {
        return ApiResponse.ok(
                "Payment screenshot noted. Keep your registered email in the WhatsApp message.",
                accessService.markPaymentSent(SecurityUtils.currentUserId(), name));
    }

    private Map<String, Object> toPlan(SubscriptionPlan plan) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", plan.getUuid());
        row.put("name", plan.getName());
        row.put("price", plan.getPrice());
        row.put("billingCycle", plan.getBillingCycle());
        row.put("features", plan.getFeaturesJson() == null ? Map.of() : plan.getFeaturesJson());
        row.put("status", plan.getStatus());
        boolean purchasable = plan.getPrice() != null
                && plan.getPrice().signum() > 0
                && !"FREE".equalsIgnoreCase(plan.getName());
        row.put("purchasable", purchasable);
        return row;
    }
}
