package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.supplier.client.InventoryCreditClient.InventoryCreditRequest;
import com.catalogstudio.wholesale.dto.ConfirmGoodsReceiptRequest;
import com.catalogstudio.wholesale.dto.GoodsReceiptResponse;
import com.catalogstudio.wholesale.entity.GoodsReceipt;
import com.catalogstudio.wholesale.entity.GoodsReceiptItem;
import com.catalogstudio.wholesale.entity.WholesaleOrder;
import com.catalogstudio.wholesale.entity.WholesaleOrderItem;
import com.catalogstudio.wholesale.repository.GoodsReceiptItemRepository;
import com.catalogstudio.wholesale.repository.GoodsReceiptRepository;
import com.catalogstudio.wholesale.repository.WholesaleOrderItemRepository;
import com.catalogstudio.wholesale.repository.WholesaleOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GoodsReceiptTx {

    private final WholesaleOrderRepository orderRepository;
    private final WholesaleOrderItemRepository orderItemRepository;
    private final GoodsReceiptRepository receiptRepository;
    private final GoodsReceiptItemRepository receiptItemRepository;

    @Transactional(readOnly = true)
    public GoodsReceiptResponse get(Long buyerUserId, UUID orderId) {
        WholesaleOrder order = requireBuyerOrder(buyerUserId, orderId, false);
        GoodsReceipt receipt = receiptRepository.findByOrderId(order.getId())
                .orElseThrow(() -> ApiException.notFound("Goods receipt not found"));
        return toResponse(order, receipt);
    }

    @Transactional
    public GoodsReceiptResponse prepare(Long buyerUserId, UUID orderId, ConfirmGoodsReceiptRequest request) {
        WholesaleOrder order = requireBuyerOrder(buyerUserId, orderId, true);
        GoodsReceipt existing = receiptRepository.findByOrderId(order.getId()).orElse(null);
        if (existing != null) {
            return toResponse(order, existing);
        }
        List<WholesaleOrderItem> items = orderItems(order);
        List<GoodsReceiptItem> lines = buildLines(items, request);
        GoodsReceipt receipt = receiptRepository.saveAndFlush(GoodsReceipt.builder()
                .orderId(order.getId())
                .buyerUserId(order.getBuyerUserId())
                .status(GoodsReceipt.Status.PENDING)
                .note(trimToNull(request.note()))
                .build());
        lines.forEach(line -> line.setReceiptId(receipt.getId()));
        receiptItemRepository.saveAllAndFlush(lines);
        return toResponse(order, receipt);
    }

    @Transactional(readOnly = true)
    public InventoryCreditRequest creditPayload(UUID receiptId) {
        GoodsReceipt receipt = receiptRepository.findByUuid(receiptId)
                .orElseThrow(() -> ApiException.notFound("Goods receipt not found"));
        Map<Long, WholesaleOrderItem> orderItems = orderItemRepository.findByOrderIdOrderByIdAsc(receipt.getOrderId()).stream()
                .collect(Collectors.toMap(WholesaleOrderItem::getId, Function.identity()));
        List<InventoryCreditRequest.Line> lines = new ArrayList<>();
        for (GoodsReceiptItem item : receiptItemRepository.findByReceiptIdOrderByIdAsc(receipt.getId())) {
            if (item.getAcceptedQty() <= 0) {
                continue;
            }
            WholesaleOrderItem source = orderItems.get(item.getOrderItemId());
            lines.add(new InventoryCreditRequest.Line(
                    item.getUuid(),
                    item.getSku(),
                    item.getProductTitle(),
                    source == null ? null : source.getColorLabel(),
                    source == null ? null : source.getSizeLabel(),
                    item.getAcceptedQty(),
                    source == null ? null : costOf(source)));
        }
        return new InventoryCreditRequest(receipt.getBuyerUserId(), receipt.getUuid(), lines);
    }

    @Transactional
    public GoodsReceiptResponse markConfirmed(UUID receiptId) {
        GoodsReceipt receipt = requireReceipt(receiptId);
        receipt.setStatus(GoodsReceipt.Status.CONFIRMED);
        receipt.setLastError(null);
        receipt.setConfirmedAt(Instant.now());
        return toResponse(requireOrder(receipt.getOrderId()), receiptRepository.save(receipt));
    }

    @Transactional
    public void markFailed(UUID receiptId, String message) {
        GoodsReceipt receipt = requireReceipt(receiptId);
        if (receipt.getStatus() == GoodsReceipt.Status.CONFIRMED) {
            return;
        }
        receipt.setStatus(GoodsReceipt.Status.FAILED);
        receipt.setLastError(trimError(message));
        receiptRepository.save(receipt);
    }

    private WholesaleOrder requireBuyerOrder(Long buyerUserId, UUID orderId, boolean lock) {
        WholesaleOrder order = (lock ? orderRepository.lockByUuid(orderId) : orderRepository.findByUuid(orderId))
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getBuyerUserId() == null || !order.getBuyerUserId().equals(buyerUserId)) {
            throw ApiException.notFound("Order not found");
        }
        if (order.getStatus() != WholesaleOrder.Status.ACCEPTED
                && order.getStatus() != WholesaleOrder.Status.FULFILLED) {
            throw ApiException.conflict("Goods can be received only after the supplier accepts the order");
        }
        return order;
    }

    private List<WholesaleOrderItem> orderItems(WholesaleOrder order) {
        return orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
    }

    static List<GoodsReceiptItem> buildLines(
            List<WholesaleOrderItem> orderItems,
            ConfirmGoodsReceiptRequest request
    ) {
        if (request.lines().size() != orderItems.size()) {
            throw ApiException.badRequest("Count every line on the order before confirming the receipt");
        }
        Map<UUID, WholesaleOrderItem> byId = orderItems.stream()
                .collect(Collectors.toMap(WholesaleOrderItem::getUuid, Function.identity()));
        Set<UUID> seen = new HashSet<>();
        List<GoodsReceiptItem> lines = new ArrayList<>();
        for (ConfirmGoodsReceiptRequest.Line input : request.lines()) {
            if (!seen.add(input.orderItemId())) {
                throw ApiException.badRequest("Duplicate order line in goods receipt");
            }
            WholesaleOrderItem source = byId.get(input.orderItemId());
            if (source == null) {
                throw ApiException.badRequest("Unknown order line in goods receipt");
            }
            GoodsReceiptMath.Line counted;
            try {
                counted = GoodsReceiptMath.compute(
                        source.getQuantity(),
                        input.receivedQty(),
                        input.damagedQty(),
                        input.rejectedQty());
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest(ex.getMessage());
            }
            if (counted.accepted() > 0 && !StringUtils.hasText(source.getSku())) {
                throw ApiException.badRequest("Accepted units need a SKU before they can enter inventory");
            }
            lines.add(GoodsReceiptItem.builder()
                    .orderItemId(source.getId())
                    .sku(trimToNull(source.getSku()))
                    .productTitle(source.getProductTitle())
                    .orderedQty(counted.ordered())
                    .receivedQty(counted.received())
                    .missingQty(counted.missing())
                    .damagedQty(counted.damaged())
                    .rejectedQty(counted.rejected())
                    .acceptedQty(counted.accepted())
                    .build());
        }
        return lines;
    }

    private GoodsReceiptResponse toResponse(WholesaleOrder order, GoodsReceipt receipt) {
        Map<Long, UUID> orderItemIds = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .collect(Collectors.toMap(WholesaleOrderItem::getId, WholesaleOrderItem::getUuid));
        List<GoodsReceiptResponse.Line> lines = receiptItemRepository.findByReceiptIdOrderByIdAsc(receipt.getId()).stream()
                .map(item -> new GoodsReceiptResponse.Line(
                        item.getUuid(),
                        orderItemIds.get(item.getOrderItemId()),
                        item.getSku(),
                        item.getProductTitle(),
                        item.getOrderedQty(),
                        item.getReceivedQty(),
                        item.getMissingQty(),
                        item.getDamagedQty(),
                        item.getRejectedQty(),
                        item.getAcceptedQty()))
                .toList();
        return new GoodsReceiptResponse(
                receipt.getUuid(),
                order.getUuid(),
                order.getOrderNumber(),
                receipt.getStatus().name(),
                receipt.getNote(),
                receipt.getLastError(),
                receipt.getConfirmedAt(),
                lines);
    }

    private GoodsReceipt requireReceipt(UUID receiptId) {
        return receiptRepository.findByUuid(receiptId)
                .orElseThrow(() -> ApiException.notFound("Goods receipt not found"));
    }

    private WholesaleOrder requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    private static BigDecimal costOf(WholesaleOrderItem source) {
        return source.getAcceptedUnitPrice() != null ? source.getAcceptedUnitPrice() : source.getUnitPrice();
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    private static String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "Retailer inventory was not updated";
        }
        String trimmed = message.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
