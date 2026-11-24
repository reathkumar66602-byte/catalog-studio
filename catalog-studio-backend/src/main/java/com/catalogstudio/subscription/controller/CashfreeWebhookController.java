package com.catalogstudio.subscription.controller;

import com.catalogstudio.subscription.service.CashfreeWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Hidden
@RestController
@RequestMapping("/api/v1/payments/cashfree")
@RequiredArgsConstructor
public class CashfreeWebhookController {

    private final CashfreeWebhookService cashfreeWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestHeader(value = "x-webhook-signature", required = false) String signature,
            @RequestHeader(value = "x-webhook-timestamp", required = false) String timestamp,
            @RequestBody String rawBody
    ) {
        try {
            cashfreeWebhookService.handle(timestamp, signature, rawBody);
            return ResponseEntity.ok("OK");
        } catch (IllegalArgumentException ex) {
            log.warn("Cashfree webhook rejected: {}", ex.getMessage());
            return ResponseEntity.status(401).body("invalid signature");
        } catch (Exception ex) {
            log.error("Cashfree webhook processing failed", ex);
            // Return 200 after signature OK to avoid endless retries on app bugs;
            // Cashfree retries on 5xx. Re-throw only unexpected infra failures.
            return ResponseEntity.status(500).body("error");
        }
    }
}
