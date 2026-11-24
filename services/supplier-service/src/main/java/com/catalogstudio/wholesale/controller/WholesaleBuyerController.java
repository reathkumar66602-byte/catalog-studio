package com.catalogstudio.wholesale.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.wholesale.dto.BillHistoryResponse;
import com.catalogstudio.wholesale.dto.OrderPreviewRequest;
import com.catalogstudio.wholesale.dto.OrderPreviewResponse;
import com.catalogstudio.wholesale.dto.OrderResponse;
import com.catalogstudio.wholesale.dto.PageResponse;
import com.catalogstudio.wholesale.dto.PlaceOrderRequest;
import com.catalogstudio.wholesale.service.WholesaleBillHistoryService;
import com.catalogstudio.wholesale.service.WholesaleOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final WholesaleBillHistoryService billHistoryService;

    @PostMapping("/orders/preview")
    public ApiResponse<OrderPreviewResponse> previewOrder(@Valid @RequestBody OrderPreviewRequest request) {
        return ApiResponse.ok(orderService.previewOrder(
                SecurityUtils.currentUserId(), request.toPlaceOrderRequest()));
    }

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

    @GetMapping("/bills")
    public ApiResponse<BillHistoryResponse> bills(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(billHistoryService.buyerBills(SecurityUtils.currentUserId(), from, to, page, size));
    }

    @GetMapping("/bills/export.xlsx")
    public ResponseEntity<byte[]> exportBills(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        byte[] bytes = billHistoryService.buyerBillsExcel(SecurityUtils.currentUserId(), from, to);
        String filename = "my-bills-" + LocalDate.now(WholesaleBillHistoryService.INDIA) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}