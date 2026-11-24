package com.catalogstudio.subscription.service;

import com.catalogstudio.subscription.dto.PriceBreakdown;
import com.catalogstudio.subscription.entity.PaymentTransaction;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionStatus;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionType;
import com.catalogstudio.subscription.entity.PaymentWebhookEvent;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.payment.CashfreeClient;
import com.catalogstudio.subscription.payment.CashfreeSignatureVerifier;
import com.catalogstudio.subscription.repository.PaymentTransactionRepository;
import com.catalogstudio.subscription.repository.PaymentWebhookEventRepository;
import com.catalogstudio.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashfreeWebhookService {

    private final CashfreeSignatureVerifier signatureVerifier;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionActivationService activationService;
    private final CashfreeClient cashfreeClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(String timestamp, String signature, String rawBody) {
        if (!signatureVerifier.valid(timestamp, rawBody, signature)) {
            log.warn("Cashfree webhook signature rejected");
            throw new IllegalArgumentException("Invalid webhook signature");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid webhook payload");
        }

        String type = text(root, "type");
        JsonNode data = root.path("data");
        JsonNode order = data.path("order");
        JsonNode payment = data.path("payment");
        String orderId = firstNonBlank(
                text(order, "order_id"),
                text(data, "order_id"),
                text(data, "link_id")
        );
        String orderStatus = firstNonBlank(text(order, "order_status"), text(data, "link_status"));
        String paymentStatus = firstNonBlank(
                text(payment, "payment_status"),
                text(order, "transaction_status"),
                text(data, "payment_status")
        );
        String paymentId = firstNonBlank(
                text(payment, "cf_payment_id"),
                text(order, "cf_payment_id"),
                text(order, "transaction_id"),
                text(data, "cf_payment_id")
        );
        String eventTime = firstNonBlank(text(root, "event_time"), timestamp);
        String eventKey = buildEventKey(type, orderId, paymentId, eventTime, rawBody);

        if (webhookEventRepository.existsByProviderAndEventKey("CASHFREE", eventKey)) {
            log.info("Cashfree webhook duplicate ignored key={}", eventKey);
            return;
        }
        webhookEventRepository.save(PaymentWebhookEvent.builder()
                .provider("CASHFREE")
                .eventKey(eventKey)
                .eventType(type)
                .linkId(orderId)
                .payloadHash(sha256(rawBody))
                .build());

        boolean paid = isPaid(type, orderStatus, paymentStatus);
        if (!paid) {
            log.info("Cashfree webhook ignored type={} orderStatus={} paymentStatus={} orderId={}",
                    type, orderStatus, paymentStatus, orderId);
            return;
        }
        if (!StringUtils.hasText(orderId)) {
            log.warn("Cashfree paid webhook missing order_id");
            return;
        }

        PaymentTransaction checkout = paymentTransactionRepository.findByGatewayLinkId(orderId)
                .orElse(null);
        if (checkout == null) {
            JsonNode remote = cashfreeClient.fetchOrder(orderId);
            log.warn("No local checkout for Cashfree order {} remoteStatus={}",
                    orderId, text(remote, "order_status"));
            return;
        }
        if (paymentTransactionRepository.existsByReferenceAndTypeAndStatus(
                orderId, TransactionType.ACTIVATION, TransactionStatus.SUCCESS)) {
            log.info("Cashfree order {} already activated", orderId);
            return;
        }

        checkout.setStatus(TransactionStatus.SUCCESS);
        checkout.setGatewayOrderId(orderId);
        checkout.setGatewayPaymentId(paymentId);
        checkout.setPaidAt(Instant.now());
        checkout.setNotes(trimNotes("Cashfree paid · order=" + orderId + " · payment=" + nullToDash(paymentId)));
        paymentTransactionRepository.save(checkout);

        User user = checkout.getUser();
        SubscriptionPlan plan = checkout.getPlan();
        if (plan == null) {
            plan = activationService.requirePlan(checkout.getPlanName());
        }
        PriceBreakdown pricing = new PriceBreakdown(
                nz(checkout.getBaseAmount()),
                nz(checkout.getDiscountAmount()),
                nz(checkout.getServiceCharge()),
                nz(checkout.getGstAmount()),
                nz(checkout.getAmount()),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                checkout.getPromoCode(),
                null,
                null,
                checkout.getCompanyGstin(),
                null
        );

        activationService.activatePaid(
                user,
                plan,
                "CASHFREE",
                orderId,
                "Auto-activated after Cashfree payment · " + orderId,
                pricing,
                null
        );
    }

    private static boolean isPaid(String type, String orderStatus, String paymentStatus) {
        if ("PAID".equalsIgnoreCase(orderStatus) || "SUCCESS".equalsIgnoreCase(paymentStatus)) {
            return true;
        }
        return type != null && type.toUpperCase().contains("PAYMENT_SUCCESS");
    }

    private static String buildEventKey(
            String type, String orderId, String paymentId, String eventTime, String rawBody
    ) {
        String base = firstNonBlank(type, "EVENT") + "|"
                + firstNonBlank(orderId, "-") + "|"
                + firstNonBlank(paymentId, "-") + "|"
                + firstNonBlank(eventTime, sha256(rawBody));
        return base.length() <= 160 ? base : base.substring(0, 160);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return UUID_FALLBACK();
        }
    }

    private static String UUID_FALLBACK() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private static String trimNotes(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
