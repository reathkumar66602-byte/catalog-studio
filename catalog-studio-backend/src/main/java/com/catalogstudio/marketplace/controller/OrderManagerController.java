package com.catalogstudio.marketplace.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.marketplace.dto.MeeshoChannelOrderResponse;
import com.catalogstudio.marketplace.service.CourierIntegrationService;
import com.catalogstudio.marketplace.service.ListingSyncService;
import com.catalogstudio.marketplace.service.OmsAutoActionService;
import com.catalogstudio.marketplace.service.PlatformOmsService;
import com.catalogstudio.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Seller Order Manager — unified multi-channel orders + listings + courier + auto-actions. */
@RestController
@RequestMapping("/api/v1/oms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Order Manager OMS")
public class OrderManagerController {

    private final PlatformOmsService platformOmsService;
    private final ListingSyncService listingSyncService;
    private final CourierIntegrationService courierIntegrationService;
    private final OmsAutoActionService autoActionService;

    @GetMapping("/orders")
    public ApiResponse<List<MeeshoChannelOrderResponse>> orders(
            @RequestParam(defaultValue = "ALL") String channel) {
        return ApiResponse.ok(platformOmsService.listOrders(channel));
    }

    @GetMapping("/listings")
    public ApiResponse<List<Map<String, Object>>> listings() {
        return ApiResponse.ok(listingSyncService.list());
    }

    @PostMapping("/listings/sync")
    public ApiResponse<Map<String, Object>> syncListing(@RequestBody Map<String, Object> body) {
        UUID variantId = UUID.fromString(String.valueOf(body.get("variantId")));
        String channel = String.valueOf(body.get("channel"));
        BigDecimal price = body.get("price") == null ? null : new BigDecimal(String.valueOf(body.get("price")));
        return ApiResponse.ok("Listing sync finished", listingSyncService.syncVariantToChannel(variantId, channel, price));
    }

    @GetMapping("/auto-actions")
    public ApiResponse<List<?>> autoActions() {
        return ApiResponse.ok(autoActionService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping("/auto-actions")
    public ApiResponse<?> saveAutoAction(@RequestBody Map<String, Object> body) {
        UUID id = body.get("id") == null ? null : UUID.fromString(String.valueOf(body.get("id")));
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = body.get("condition") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> action = body.get("action") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of("type", "MARK_STATUS", "status", "PROCESSING");
        return ApiResponse.ok(
                "Auto-action saved",
                autoActionService.upsert(
                        SecurityUtils.currentUserId(),
                        id,
                        String.valueOf(body.getOrDefault("name", "Rule")),
                        enabled,
                        String.valueOf(body.getOrDefault("triggerEvent", "ORDER_IMPORTED")),
                        body.get("channel") == null ? null : String.valueOf(body.get("channel")),
                        condition,
                        action));
    }

    @PostMapping("/prices/sync")
    public ApiResponse<Map<String, Object>> syncPrices(@RequestBody Map<String, Object> body) {
        String channel = String.valueOf(body.get("channel"));
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = body.get("prices") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        Map<String, Double> prices = new java.util.HashMap<>();
        raw.forEach((k, v) -> prices.put(k, Double.parseDouble(String.valueOf(v))));
        int ok = autoActionService.syncPrices(SecurityUtils.currentUserId(), channel, prices);
        return ApiResponse.ok(Map.of("updated", ok, "channel", channel));
    }

    @GetMapping("/couriers")
    public ApiResponse<List<Map<String, Object>>> couriers() {
        return ApiResponse.ok(courierIntegrationService.listShipments());
    }

    @PostMapping("/orders/{orderId}/courier")
    public ApiResponse<Map<String, Object>> createCourier(
            @PathVariable UUID orderId, @RequestBody Map<String, Object> body) {
        String provider = String.valueOf(body.getOrDefault("provider", "MANUAL"));
        return ApiResponse.ok(
                "Courier shipment created",
                courierIntegrationService.createShipment(orderId, provider));
    }
}
