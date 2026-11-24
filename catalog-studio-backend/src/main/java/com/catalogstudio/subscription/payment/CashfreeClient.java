package com.catalogstudio.subscription.payment;

import com.catalogstudio.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CashfreeClient {

    private final CashfreeProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public OrderCreated createOrder(PaymentCheckoutCommand command) {
        if (!properties.configured()) {
            throw ApiException.badRequest("Cashfree payment gateway is not configured");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("order_id", command.linkId());
        body.put("order_amount", command.amount());
        body.put("order_currency", StringUtils.hasText(command.currency()) ? command.currency() : "INR");
        body.put("order_note", "Catalog Studio " + command.planName() + " plan");

        ObjectNode customer = body.putObject("customer_details");
        customer.put("customer_id", "u" + command.userId());
        customer.put("customer_phone", command.customerPhone());
        if (StringUtils.hasText(command.customerEmail())) {
            customer.put("customer_email", command.customerEmail());
        }
        if (StringUtils.hasText(command.customerName())) {
            customer.put("customer_name", command.customerName());
        }

        ObjectNode meta = body.putObject("order_meta");
        if (StringUtils.hasText(command.returnUrl())) {
            meta.put("return_url", command.returnUrl());
        }
        if (StringUtils.hasText(command.notifyUrl())) {
            meta.put("notify_url", command.notifyUrl());
        }

        if (command.notes() != null && !command.notes().isEmpty()) {
            ObjectNode tags = body.putObject("order_tags");
            for (Map.Entry<String, String> entry : command.notes().entrySet()) {
                if (StringUtils.hasText(entry.getKey()) && entry.getValue() != null) {
                    // Cashfree tags values max length is short; keep compact.
                    String value = entry.getValue();
                    tags.put(entry.getKey(), value.length() > 64 ? value.substring(0, 64) : value);
                }
            }
        }

        try {
            String response = restClient.post()
                    .uri(properties.resolvedBaseUrl() + "/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::authHeaders)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String orderId = root.path("order_id").asText(command.linkId());
            String sessionId = root.path("payment_session_id").asText(null);
            String cfOrderId = root.path("cf_order_id").asText(null);
            String status = root.path("order_status").asText("");
            if (!StringUtils.hasText(sessionId)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Cashfree did not return a payment session");
            }
            log.info("Cashfree order created orderId={} cfOrderId={} status={}", orderId, cfOrderId, status);
            return new OrderCreated(orderId, cfOrderId, sessionId, status);
        } catch (RestClientResponseException ex) {
            log.warn("Cashfree create order failed HTTP {} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not start Cashfree payment. Please try again.");
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Cashfree create order failed", ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not start Cashfree payment. Please try again.");
        }
    }

    public JsonNode fetchOrder(String orderId) {
        try {
            String response = restClient.get()
                    .uri(properties.resolvedBaseUrl() + "/orders/{orderId}", orderId)
                    .headers(this::authHeaders)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (Exception ex) {
            log.warn("Cashfree fetch order failed for {}: {}", orderId, ex.toString());
            return objectMapper.createObjectNode();
        }
    }

    private void authHeaders(org.springframework.http.HttpHeaders headers) {
        headers.add("x-client-id", properties.appId().trim());
        headers.add("x-client-secret", properties.secretKey().trim());
        headers.add("x-api-version", properties.apiVersion());
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    }

    public record OrderCreated(String orderId, String cfOrderId, String paymentSessionId, String status) {}
}
