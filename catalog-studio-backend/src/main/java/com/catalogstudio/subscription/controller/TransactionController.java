package com.catalogstudio.subscription.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.subscription.dto.TransactionHistoryItem;
import com.catalogstudio.subscription.service.PaymentTransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions")
public class TransactionController {

    private final PaymentTransactionService paymentTransactionService;

    @GetMapping
    public ApiResponse<Page<TransactionHistoryItem>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        return ApiResponse.ok(paymentTransactionService.history(
                SecurityUtils.currentUserId(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
}
