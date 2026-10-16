package com.catalogstudio.wholesale.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.wholesale.dto.OrderResponse;
import com.catalogstudio.wholesale.dto.PageResponse;
import com.catalogstudio.wholesale.dto.PlaceOrderRequest;
import com.catalogstudio.wholesale.service.WholesaleOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wholesale/buyer")
@RequiredArgsConstructor
@Tag(name = "Wholesale buyer")
public class WholesaleBuyerController {

    private final WholesaleOrderService orderService;

    @PostMapping("/orders")
    public ApiResponse<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return ApiResponse.ok("Order placed", orderService.placeOrder(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderResponse>> orders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(orderService.listBuyerOrders(SecurityUtils.currentUserId(), page, size));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderResponse> order(@PathVariable UUID id) {
        return ApiResponse.ok(orderService.getBuyerOrder(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok("Order cancelled", orderService.cancelOrder(SecurityUtils.currentUserId(), id));
    }
}