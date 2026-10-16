package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.wholesale.dto.AcceptOrderRequest;
import com.catalogstudio.wholesale.dto.OrderResponse;
import com.catalogstudio.wholesale.dto.PageResponse;
import com.catalogstudio.wholesale.dto.PlaceOrderRequest;
import com.catalogstudio.wholesale.dto.RejectOrderRequest;
import com.catalogstudio.wholesale.entity.WholesaleOrder;
import com.catalogstudio.wholesale.entity.WholesaleOrderItem;
import com.catalogstudio.wholesale.entity.WholesaleProduct;
import com.catalogstudio.wholesale.entity.WholesaleStore;
import com.catalogstudio.wholesale.entity.WholesaleVariant;
import com.catalogstudio.wholesale.repository.WholesaleOrderItemRepository;
import com.catalogstudio.wholesale.repository.WholesaleOrderRepository;
import com.catalogstudio.wholesale.repository.WholesaleProductRepository;
import com.catalogstudio.wholesale.repository.WholesaleStoreRepository;
import com.catalogstudio.wholesale.repository.WholesaleVariantRepository;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Booking flow with reserved stock.
 * place: reserved += qty (versioned). accept: reserved -= qty and on_hand -= qty. reject / cancel: reserved -= qty.
 */
@Service
@RequiredArgsConstructor
public class WholesaleOrderService {

    private final WholesaleAccessService accessService;
    private final WholesaleStoreRepository storeRepository;
    private final WholesaleProductRepository productRepository;
    private final WholesaleVariantRepository variantRepository;
    private final WholesaleOrderRepository orderRepository;
    private final WholesaleOrderItemRepository orderItemRepository;
    private final WholesaleMapper mapper;

    // --- buyer ----------------------------------------------------------------------------

    @Transactional
    public OrderResponse placeOrder(Long buyerUserId, PlaceOrderRequest request) {
        accessService.requireLiveStockBuyer(buyerUserId);

        // Merge duplicate variant lines.
        Map<UUID, Integer> requested = new LinkedHashMap<>();
        for (PlaceOrderRequest.Item item : request.items()) {
            if (item.quantity() > 0) {
                requested.merge(item.variantId(), item.quantity(), Integer::sum);
            }
        }
        if (requested.isEmpty()) {
            throw ApiException.badRequest("Select at least one size with a quantity");
        }

        List<WholesaleVariant> variants = variantRepository.findByUuidIn(requested.keySet());
        if (variants.size() != requested.size()) {
            throw ApiException.notFound("One or more items are no longer available");
        }
        // Stable lock/update order avoids deadlocks between concurrent orders.
        variants = variants.stream().sorted(Comparator.comparing(WholesaleVariant::getId)).toList();

        Map<Long, WholesaleProduct> products = productRepository
                .findAllById(variants.stream().map(WholesaleVariant::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(WholesaleProduct::getId, p -> p));
        Set<Long> storeIds = products.values().stream().map(WholesaleProduct::getStoreId).collect(Collectors.toSet());
        if (storeIds.size() != 1) {
            throw ApiException.badRequest("Place one order per supplier store");
        }
        WholesaleStore store = storeRepository.findById(storeIds.iterator().next())
                .orElseThrow(() -> ApiException.notFound("Store not found"));
        if (store.getStatus() != WholesaleStore.Status.ACTIVE) {
            throw ApiException.badRequest("This supplier is not accepting orders right now");
        }
        if (store.getOwnerUserId().equals(buyerUserId)) {
            throw ApiException.badRequest("You cannot book stock from your own store");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<WholesaleOrderItem> items = new ArrayList<>(variants.size());
        Map<Long, Integer> bookedByProduct = new HashMap<>();
        for (WholesaleVariant variant : variants) {
            WholesaleProduct product = products.get(variant.getProductId());
            if (product == null || product.getStatus() != WholesaleProduct.Status.ACTIVE
                    || variant.getStatus() != WholesaleVariant.Status.ACTIVE) {
                throw ApiException.badRequest("Some items are no longer available");
            }
            int qty = requested.get(variant.getUuid());
            if (variant.available() < qty) {
                throw ApiException.conflict("Only " + variant.available() + " pcs left for "
                        + product.getTitle() + " (" + variant.getColorLabel() + " / " + variant.getSizeLabel() + ")");
            }
            variant.setReserved(variant.getReserved() + qty);
            saveVariant(variant);

            BigDecimal line = variant.getPrice().multiply(BigDecimal.valueOf(qty));
            subtotal = subtotal.add(line);
            bookedByProduct.merge(product.getId(), qty, Integer::sum);
            items.add(WholesaleOrderItem.builder()
                    .variantId(variant.getId())
                    .productId(product.getId())
                    .productTitle(product.getTitle())
                    .colorLabel(variant.getColorLabel())
                    .sizeLabel(variant.getSizeLabel())
                    .sku(variant.getSku())
                    .unitPrice(variant.getPrice())
                    .quantity(qty)
                    .lineTotal(line)
                    .build());
        }

        WholesaleOrder order = orderRepository.saveAndFlush(WholesaleOrder.builder()
                .orderNumber(uniqueOrderNumber())
                .buyerUserId(buyerUserId)
                .storeId(store.getId())
                .status(WholesaleOrder.Status.PLACED)
                .buyerNote(trimToNull(request.note()))
                .subtotal(subtotal)
                .currency("INR")
                .placedAt(Instant.now())
                .build());
        for (WholesaleOrderItem item : items) {
            item.setOrderId(order.getId());
        }
        orderItemRepository.saveAllAndFlush(items);

        bookedByProduct.forEach(productRepository::adjustBookedCount);
        refreshProducts(bookedByProduct.keySet());
        return mapper.orders(List.of(order), true).get(0);
    }

    @Transactional
    public OrderResponse cancelOrder(Long buyerUserId, UUID orderId) {
        WholesaleOrder order = orderRepository.lockByUuid(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getBuyerUserId().equals(buyerUserId)) {
            throw ApiException.notFound("Order not found");
        }
        if (order.getStatus() != WholesaleOrder.Status.PLACED) {
            throw ApiException.conflict("Only orders awaiting supplier acceptance can be cancelled");
        }
        order.setStatus(WholesaleOrder.Status.CANCELLED);
        orderRepository.saveAndFlush(order);
        releaseReserved(order);
        return mapper.orders(List.of(order), true).get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listBuyerOrders(Long buyerUserId, int page, int size) {
        Page<WholesaleOrder> result = orderRepository.findByBuyerUserId(buyerUserId, pageable(page, size));
        return PageResponse.of(result, mapper.orders(result.getContent(), true));
    }

    @Transactional(readOnly = true)
    public OrderResponse getBuyerOrder(Long buyerUserId, UUID orderId) {
        WholesaleOrder order = orderRepository.findByUuid(orderId)
                .filter(o -> o.getBuyerUserId().equals(buyerUserId))
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        return mapper.orders(List.of(order), true).get(0);
    }

    // --- supplier -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listSupplierOrders(Long supplierUserId, String status, int page, int size) {
        WholesaleStore store = requireOwnStore(supplierUserId);
        PageRequest pageable = pageable(page, size);
        Page<WholesaleOrder> result = StringUtils.hasText(status)
                ? orderRepository.findByStoreIdAndStatus(store.getId(), parseStatus(status), pageable)
                : orderRepository.findByStoreId(store.getId(), pageable);
        return PageResponse.of(result, mapper.orders(result.getContent(), false));
    }

    @Transactional(readOnly = true)
    public OrderResponse getSupplierOrder(Long supplierUserId, UUID orderId) {
        WholesaleStore store = requireOwnStore(supplierUserId);
        WholesaleOrder order = orderRepository.findByUuid(orderId)
                .filter(o -> o.getStoreId().equals(store.getId()))
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        return mapper.orders(List.of(order), false).get(0);
    }

    @Transactional
    public OrderResponse acceptOrder(Long supplierUserId, UUID orderId, AcceptOrderRequest request) {
        WholesaleOrder order = lockOwnedOrder(supplierUserId, orderId);
        if (order.getStatus() != WholesaleOrder.Status.PLACED) {
            throw ApiException.conflict("This order is already " + order.getStatus().name().toLowerCase(Locale.ROOT));
        }
        List<WholesaleOrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        Map<Long, WholesaleVariant> variants = loadVariants(items);

        // Move reserved -> sold.
        Set<Long> productIds = new java.util.HashSet<>();
        for (WholesaleOrderItem item : items.stream().sorted(Comparator.comparing(WholesaleOrderItem::getVariantId)).toList()) {
            WholesaleVariant variant = variants.get(item.getVariantId());
            productIds.add(item.getProductId());
            if (variant == null) {
                continue;
            }
            if (variant.getReserved() < item.getQuantity() || variant.getOnHand() < item.getQuantity()) {
                throw ApiException.conflict("Stock for " + item.getProductTitle() + " changed. Please review the order.");
            }
            variant.setReserved(variant.getReserved() - item.getQuantity());
            variant.setOnHand(variant.getOnHand() - item.getQuantity());
            saveVariant(variant);
        }

        applyAcceptedPricing(order, items, request);
        Instant now = Instant.now();
        order.setStatus(WholesaleOrder.Status.ACCEPTED);
        order.setAcceptedAt(now);
        if (request != null && StringUtils.hasText(request.supplierNote())) {
            order.setSupplierNote(request.supplierNote().trim());
        }
        orderItemRepository.saveAll(items);
        orderRepository.saveAndFlush(order);
        refreshProducts(productIds);
        return mapper.orders(List.of(order), false).get(0);
    }

    @Transactional
    public OrderResponse rejectOrder(Long supplierUserId, UUID orderId, RejectOrderRequest request) {
        WholesaleOrder order = lockOwnedOrder(supplierUserId, orderId);
        if (order.getStatus() != WholesaleOrder.Status.PLACED) {
            throw ApiException.conflict("This order is already " + order.getStatus().name().toLowerCase(Locale.ROOT));
        }
        order.setStatus(WholesaleOrder.Status.REJECTED);
        order.setRejectedAt(Instant.now());
        if (request != null && StringUtils.hasText(request.supplierNote())) {
            order.setSupplierNote(request.supplierNote().trim());
        }
        orderRepository.saveAndFlush(order);
        releaseReserved(order);
        return mapper.orders(List.of(order), false).get(0);
    }

    @Transactional
    public OrderResponse fulfillOrder(Long supplierUserId, UUID orderId) {
        WholesaleOrder order = lockOwnedOrder(supplierUserId, orderId);
        if (order.getStatus() != WholesaleOrder.Status.ACCEPTED) {
            throw ApiException.conflict("Only accepted orders can be marked fulfilled");
        }
        order.setStatus(WholesaleOrder.Status.FULFILLED);
        orderRepository.saveAndFlush(order);
        return mapper.orders(List.of(order), false).get(0);
    }

    // --- internals ------------------------------------------------------------------------

    private void applyAcceptedPricing(WholesaleOrder order, List<WholesaleOrderItem> items, AcceptOrderRequest request) {
        boolean hasLines = request != null && request.lines() != null && !request.lines().isEmpty();
        if (hasLines) {
            Map<UUID, BigDecimal> prices = new HashMap<>();
            for (AcceptOrderRequest.Line line : request.lines()) {
                prices.put(line.itemId(), line.unitPrice());
            }
            Set<UUID> known = items.stream().map(WholesaleOrderItem::getUuid).collect(Collectors.toSet());
            for (UUID id : prices.keySet()) {
                if (!known.contains(id)) {
                    throw ApiException.badRequest("Unknown order line in accepted prices");
                }
            }
            BigDecimal total = BigDecimal.ZERO;
            for (WholesaleOrderItem item : items) {
                BigDecimal unit = prices.getOrDefault(item.getUuid(), item.getUnitPrice());
                item.setAcceptedUnitPrice(unit);
                total = total.add(unit.multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            order.setAcceptedTotal(total);
        } else if (request != null && request.acceptedTotal() != null) {
            order.setAcceptedTotal(request.acceptedTotal());
            for (WholesaleOrderItem item : items) {
                item.setAcceptedUnitPrice(item.getUnitPrice());
            }
        } else {
            for (WholesaleOrderItem item : items) {
                item.setAcceptedUnitPrice(item.getUnitPrice());
            }
            order.setAcceptedTotal(order.getSubtotal());
        }
    }

    /** Releases reserved stock and booked counters for an open order (reject / cancel). Clears the persistence context. */
    private void releaseReserved(WholesaleOrder order) {
        List<WholesaleOrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        Map<Long, WholesaleVariant> variants = loadVariants(items);
        Map<Long, Integer> bookedByProduct = new TreeMap<>();
        for (WholesaleOrderItem item : items.stream().sorted(Comparator.comparing(WholesaleOrderItem::getVariantId)).toList()) {
            bookedByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            WholesaleVariant variant = variants.get(item.getVariantId());
            if (variant == null) {
                continue;
            }
            variant.setReserved(Math.max(0, variant.getReserved() - item.getQuantity()));
            saveVariant(variant);
        }
        bookedByProduct.forEach((productId, qty) -> productRepository.adjustBookedCount(productId, -qty));
        refreshProducts(bookedByProduct.keySet());
    }

    private Map<Long, WholesaleVariant> loadVariants(List<WholesaleOrderItem> items) {
        return variantRepository
                .findAllById(items.stream().map(WholesaleOrderItem::getVariantId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(WholesaleVariant::getId, v -> v));
    }

    /** Must run last in a transaction: the statement clears the persistence context. */
    private void refreshProducts(Iterable<Long> productIds) {
        for (Long productId : productIds) {
            productRepository.refreshAggregates(productId);
        }
    }

    private void saveVariant(WholesaleVariant variant) {
        try {
            variantRepository.saveAndFlush(variant);
        } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
            throw ApiException.conflict("Stock just changed for " + variant.getSku() + ". Please refresh and try again.");
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("Stock changed while booking. Please refresh and try again.");
        }
    }

    private WholesaleStore requireOwnStore(Long userId) {
        return storeRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.forbidden("You do not have a wholesale store"));
    }

    private WholesaleOrder lockOwnedOrder(Long supplierUserId, UUID orderId) {
        WholesaleStore store = requireOwnStore(supplierUserId);
        WholesaleOrder order = orderRepository.lockByUuid(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getStoreId().equals(store.getId())) {
            throw ApiException.notFound("Order not found");
        }
        return order;
    }

    private String uniqueOrderNumber() {
        String number = WholesaleSlugs.orderNumber();
        while (orderRepository.existsByOrderNumber(number)) {
            number = WholesaleSlugs.orderNumber();
        }
        return number;
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0), WholesaleProductService.clampSize(size),
                Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id")));
    }

    private static WholesaleOrder.Status parseStatus(String status) {
        try {
            return WholesaleOrder.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown status: " + status);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
