package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.email.service.MailDispatchService;
import com.catalogstudio.storage.StorageService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import com.catalogstudio.wholesale.dto.AcceptOrderRequest;
import com.catalogstudio.wholesale.dto.BillEditRequest;
import com.catalogstudio.wholesale.dto.ManualBillRequest;
import com.catalogstudio.wholesale.dto.OrderPreviewResponse;
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
import com.catalogstudio.wholesale.service.WholesaleBillPdfService.StoredBill;
import com.catalogstudio.wholesale.service.WholesaleCouponService.AppliedCoupon;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Manual bills deduct on_hand immediately (no reserve) and mark the order fulfilled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WholesaleOrderService {

    private static final RoundingMode MONEY = RoundingMode.HALF_UP;
    private static final BigDecimal DEFAULT_GST = new BigDecimal("18.00");

    private final WholesaleAccessService accessService;
    private final WholesaleStoreRepository storeRepository;
    private final WholesaleProductRepository productRepository;
    private final WholesaleVariantRepository variantRepository;
    private final WholesaleOrderRepository orderRepository;
    private final WholesaleOrderItemRepository orderItemRepository;
    private final WholesaleCouponService couponService;
    private final UserRepository userRepository;
    private final WholesaleMapper mapper;
    private final WholesaleBillPdfService billPdfService;
    private final MailDispatchService mailDispatchService;
    private final StorageService storageService;

    // --- buyer ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public OrderPreviewResponse previewOrder(Long buyerUserId, PlaceOrderRequest request) {
        accessService.requireLiveStockBuyer(buyerUserId);
        resolveBuyerMobile(buyerUserId, request.mobile(), false);
        PreparedCart cart = prepareCart(buyerUserId, request, false);
        AppliedCoupon applied = couponService.apply(cart.store().getId(), request.couponCode(), cart.subtotal(), false);
        BillTotals bill = computeBill(cart.subtotal(), applied.discountAmount(), cart.store().getGstPercent());
        return new OrderPreviewResponse(
                bill.subtotal(),
                bill.discountAmount(),
                bill.taxableAmount(),
                bill.cgstAmount(),
                bill.sgstAmount(),
                bill.gstPercent(),
                bill.grandTotal(),
                applied.code(),
                "INR");
    }

    @Transactional
    public OrderResponse placeOrder(Long buyerUserId, PlaceOrderRequest request) {
        accessService.requireLiveStockBuyer(buyerUserId);

        String buyerMobile = resolveBuyerMobile(buyerUserId, request.mobile(), true);
        PreparedCart cart = prepareCart(buyerUserId, request, true);

        AppliedCoupon applied = couponService.apply(cart.store().getId(), request.couponCode(), cart.subtotal(), true);
        BillTotals bill = computeBill(cart.subtotal(), applied.discountAmount(), cart.store().getGstPercent());

        User buyer = userRepository.findById(buyerUserId).orElse(null);
        Instant now = Instant.now();
        WholesaleOrder order = orderRepository.saveAndFlush(WholesaleOrder.builder()
                .orderNumber(uniqueOrderNumber())
                .buyerUserId(buyerUserId)
                .storeId(cart.store().getId())
                .status(WholesaleOrder.Status.PLACED)
                .source(WholesaleOrder.Source.ONLINE)
                .buyerNote(trimToNull(request.note()))
                .subtotal(bill.subtotal())
                .discountAmount(bill.discountAmount())
                .taxableAmount(bill.taxableAmount())
                .cgstAmount(bill.cgstAmount())
                .sgstAmount(bill.sgstAmount())
                .gstPercent(bill.gstPercent())
                .grandTotal(bill.grandTotal())
                .couponCode(applied.code())
                .buyerName(buyer != null ? trimToNull(buyer.getName()) : null)
                .buyerEmail(buyer != null ? trimToNull(buyer.getEmail()) : null)
                .buyerMobile(buyerMobile)
                .buyerMobileUpdatedAt(now)
                .currency("INR")
                .placedAt(now)
                .build());
        for (WholesaleOrderItem item : cart.items()) {
            item.setOrderId(order.getId());
        }
        orderItemRepository.saveAllAndFlush(cart.items());

        if (applied.present()) {
            couponService.redeem(applied.coupon(), order.getId(), applied.discountAmount());
        }

        cart.bookedByProduct().forEach(productRepository::adjustBookedCount);
        refreshProducts(cart.bookedByProduct().keySet());
        return mapper.orders(List.of(order), true).get(0);
    }

    @Transactional
    public OrderResponse cancelOrder(Long buyerUserId, UUID orderId) {
        WholesaleOrder order = orderRepository.lockByUuid(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getBuyerUserId() == null || !order.getBuyerUserId().equals(buyerUserId)) {
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
                .filter(o -> Objects.equals(o.getBuyerUserId(), buyerUserId))
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

    @Transactional(readOnly = true)
    public String getBillPdfUrl(Long supplierUserId, UUID orderId) {
        WholesaleStore store = requireOwnStore(supplierUserId);
        WholesaleOrder order = orderRepository.findByUuid(orderId)
                .filter(o -> o.getStoreId().equals(store.getId()))
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!StringUtils.hasText(order.getBillPdfUrl())) {
            throw ApiException.notFound("Bill PDF is not available for this order");
        }
        return order.getBillPdfUrl();
    }

    @Transactional
    public OrderResponse placeManualBill(Long supplierUserId, ManualBillRequest request) {
        WholesaleStore store = requireOwnStore(supplierUserId);
        PreparedManualCart cart = prepareManualCart(store, request);
        BillTotals bill = computeBill(cart.subtotal(), BigDecimal.ZERO, request.gstPercent());

        Instant now = Instant.now();
        WholesaleOrder order = orderRepository.saveAndFlush(WholesaleOrder.builder()
                .orderNumber(uniqueOrderNumber())
                .buyerUserId(null)
                .storeId(store.getId())
                .status(WholesaleOrder.Status.FULFILLED)
                .source(WholesaleOrder.Source.MANUAL)
                .buyerNote(trimToNull(request.note()))
                .subtotal(bill.subtotal())
                .discountAmount(bill.discountAmount())
                .taxableAmount(bill.taxableAmount())
                .cgstAmount(bill.cgstAmount())
                .sgstAmount(bill.sgstAmount())
                .gstPercent(bill.gstPercent())
                .grandTotal(bill.grandTotal())
                .acceptedTotal(bill.subtotal())
                .buyerName(request.buyerName().trim())
                .buyerEmail(trimToNull(request.buyerEmail()))
                .buyerMobile(request.buyerMobile().trim())
                .buyerMobileUpdatedAt(now)
                .currency("INR")
                .placedAt(now)
                .acceptedAt(now)
                .paidAt(now)
                .build());
        for (WholesaleOrderItem item : cart.items()) {
            item.setOrderId(order.getId());
            item.setAcceptedUnitPrice(item.getUnitPrice());
        }
        List<WholesaleOrderItem> savedItems = orderItemRepository.saveAllAndFlush(cart.items());

        attachBillPdfAndMaybeEmail(store, order, savedItems);

        refreshProducts(cart.productIds());
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
        BigDecimal pricingBase = order.getAcceptedTotal() != null ? order.getAcceptedTotal() : order.getSubtotal();
        BillTotals bill = computeBill(pricingBase, nz(order.getDiscountAmount()), order.getGstPercent());
        applyBill(order, bill);

        Instant now = Instant.now();
        order.setStatus(WholesaleOrder.Status.ACCEPTED);
        order.setAcceptedAt(now);
        if (request != null && StringUtils.hasText(request.supplierNote())) {
            order.setSupplierNote(request.supplierNote().trim());
        }
        enrichBuyerSnapshotFromUser(order);
        orderItemRepository.saveAll(items);
        orderRepository.saveAndFlush(order);

        WholesaleStore store = storeRepository.findById(order.getStoreId())
                .orElseThrow(() -> ApiException.notFound("Store not found"));
        attachBillPdfAndMaybeEmail(store, order, items);

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

    @Transactional
    public OrderResponse editBill(Long supplierUserId, UUID orderId, BillEditRequest request) {
        WholesaleOrder order = lockOwnedOrder(supplierUserId, orderId);
        if (order.getStatus() != WholesaleOrder.Status.ACCEPTED
                && order.getStatus() != WholesaleOrder.Status.FULFILLED) {
            throw ApiException.conflict("Only accepted or fulfilled bills can be edited");
        }
        List<WholesaleOrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        Map<UUID, BillEditRequest.Line> byItem = new LinkedHashMap<>();
        for (BillEditRequest.Line line : request.lines()) {
            byItem.put(line.itemId(), line);
        }
        if (byItem.size() != items.size()) {
            throw ApiException.badRequest("Include every line on the bill");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (WholesaleOrderItem item : items) {
            BillEditRequest.Line line = byItem.get(item.getUuid());
            if (line == null) {
                throw ApiException.badRequest("Unknown bill line: " + item.getUuid());
            }
            BigDecimal unit = money(line.unitPrice());
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setAcceptedUnitPrice(unit);
            item.setUnitPrice(unit);
            item.setLineTotal(lineTotal);
            if (line.hsnSac() != null) {
                item.setHsnSac(trimToNull(line.hsnSac()));
            }
            subtotal = subtotal.add(lineTotal);
        }
        order.setBuyerName(request.buyerName().trim());
        order.setBuyerMobile(request.buyerMobile().trim());
        order.setBuyerEmail(trimToNull(request.buyerEmail()));
        order.setBuyerGstin(trimToNull(request.buyerGstin()));
        order.setBuyerAddress(trimToNull(request.buyerAddress()));
        if (request.supplierNote() != null) {
            order.setSupplierNote(trimToNull(request.supplierNote()));
        }
        order.setSubtotal(money(subtotal));
        order.setAcceptedTotal(money(subtotal));
        BillTotals bill = computeBill(money(subtotal), nz(order.getDiscountAmount()), request.gstPercent());
        applyBill(order, bill);
        orderItemRepository.saveAllAndFlush(items);
        orderRepository.saveAndFlush(order);

        WholesaleStore store = storeRepository.findById(order.getStoreId())
                .orElseThrow(() -> ApiException.notFound("Store not found"));
        replaceBillPdf(store, order, items);
        return mapper.orders(List.of(order), false).get(0);
    }

    // --- internals ------------------------------------------------------------------------

    private PreparedManualCart prepareManualCart(WholesaleStore store, ManualBillRequest request) {
        Map<UUID, ManualBillRequest.Item> requested = new LinkedHashMap<>();
        for (ManualBillRequest.Item item : request.items()) {
            if (item.quantity() > 0) {
                ManualBillRequest.Item existing = requested.get(item.variantId());
                if (existing == null) {
                    requested.put(item.variantId(), item);
                } else {
                    BigDecimal price = item.unitPrice() != null ? item.unitPrice() : existing.unitPrice();
                    requested.put(item.variantId(), new ManualBillRequest.Item(
                            item.variantId(),
                            existing.quantity() + item.quantity(),
                            price));
                }
            }
        }
        if (requested.isEmpty()) {
            throw ApiException.badRequest("Select at least one size with a quantity");
        }

        List<WholesaleVariant> variants = variantRepository.findByUuidIn(requested.keySet());
        if (variants.size() != requested.size()) {
            throw ApiException.notFound("One or more items are no longer available");
        }
        variants = variants.stream().sorted(Comparator.comparing(WholesaleVariant::getId)).toList();

        Map<Long, WholesaleProduct> products = productRepository
                .findAllById(variants.stream().map(WholesaleVariant::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(WholesaleProduct::getId, p -> p));

        BigDecimal subtotal = BigDecimal.ZERO;
        List<WholesaleOrderItem> items = new ArrayList<>(variants.size());
        Set<Long> productIds = new java.util.HashSet<>();
        for (WholesaleVariant variant : variants) {
            WholesaleProduct product = products.get(variant.getProductId());
            if (product == null || !product.getStoreId().equals(store.getId())) {
                throw ApiException.badRequest("Some items do not belong to your store");
            }
            if (product.getStatus() == WholesaleProduct.Status.ARCHIVED
                    || variant.getStatus() != WholesaleVariant.Status.ACTIVE) {
                throw ApiException.badRequest("Some items are no longer available");
            }
            ManualBillRequest.Item req = requested.get(variant.getUuid());
            int qty = req.quantity();
            if (variant.available() < qty) {
                throw ApiException.conflict("Only " + variant.available() + " pcs left for "
                        + product.getTitle() + " (" + variant.getColorLabel() + " / " + variant.getSizeLabel() + ")");
            }
            // Deduct on_hand immediately (sold), do not touch reserved.
            variant.setOnHand(variant.getOnHand() - qty);
            saveVariant(variant);

            BigDecimal unit = req.unitPrice() != null ? money(req.unitPrice()) : money(variant.getPrice());
            BigDecimal line = unit.multiply(BigDecimal.valueOf(qty));
            subtotal = subtotal.add(line);
            productIds.add(product.getId());
            items.add(WholesaleOrderItem.builder()
                    .variantId(variant.getId())
                    .productId(product.getId())
                    .productTitle(product.getTitle())
                    .colorLabel(variant.getColorLabel())
                    .sizeLabel(variant.getSizeLabel())
                    .sku(variant.getSku())
                    .unitPrice(unit)
                    .quantity(qty)
                    .lineTotal(line)
                    .build());
        }
        return new PreparedManualCart(money(subtotal), items, productIds);
    }

    private PreparedCart prepareCart(Long buyerUserId, PlaceOrderRequest request, boolean reserveStock) {
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
            if (reserveStock) {
                variant.setReserved(variant.getReserved() + qty);
                saveVariant(variant);
            }

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
        return new PreparedCart(store, money(subtotal), items, bookedByProduct);
    }

    private void attachBillPdfAndMaybeEmail(
            WholesaleStore store, WholesaleOrder order, List<WholesaleOrderItem> items) {
        replaceBillPdf(store, order, items);
        if (StringUtils.hasText(order.getBuyerEmail())) {
            boolean sent = sendBillEmail(order, store);
            if (sent) {
                order.setBillEmailedAt(Instant.now());
            }
        }
        orderRepository.saveAndFlush(order);
    }

    private void replaceBillPdf(
            WholesaleStore store, WholesaleOrder order, List<WholesaleOrderItem> items) {
        if (StringUtils.hasText(order.getBillPdfStorageKey())) {
            try {
                storageService.delete(order.getBillPdfStorageKey());
            } catch (RuntimeException ignored) {
                // best-effort cleanup of previous PDF
            }
        }
        StoredBill stored = billPdfService.generateAndStore(store, order, items);
        order.setBillPdfUrl(stored.publicUrl());
        order.setBillPdfStorageKey(stored.storageKey());
        orderRepository.saveAndFlush(order);
    }

    private boolean sendBillEmail(WholesaleOrder order, WholesaleStore store) {
        String to = order.getBuyerEmail().trim();
        String subject = "Invoice " + order.getOrderNumber() + " from " + store.getStoreName();
        String link = order.getBillPdfUrl();
        String html = """
                <p>Hello %s,</p>
                <p>Your GST invoice <strong>%s</strong> from <strong>%s</strong> is ready.</p>
                <p>Grand total: <strong>INR %s</strong></p>
                <p><a href="%s">Download bill PDF</a></p>
                <p>If the link does not open, copy and paste this URL into your browser:<br/>%s</p>
                """.formatted(
                escape(StringUtils.hasText(order.getBuyerName()) ? order.getBuyerName() : "there"),
                escape(order.getOrderNumber()),
                escape(store.getStoreName()),
                nz(order.getGrandTotal()).toPlainString(),
                escape(link),
                escape(link));
        String text = "Invoice " + order.getOrderNumber() + " from " + store.getStoreName()
                + ". Download: " + link;
        try {
            return mailDispatchService.sendHtml(to, subject, html, text);
        } catch (Exception ex) {
            log.warn("Failed to email bill {} to {}: {}", order.getOrderNumber(), to, ex.toString());
            return false;
        }
    }

    private void enrichBuyerSnapshotFromUser(WholesaleOrder order) {
        if (order.getBuyerUserId() == null) {
            return;
        }
        userRepository.findById(order.getBuyerUserId()).ifPresent(user -> {
            if (!StringUtils.hasText(order.getBuyerName())) {
                order.setBuyerName(trimToNull(user.getName()));
            }
            if (!StringUtils.hasText(order.getBuyerEmail())) {
                order.setBuyerEmail(trimToNull(user.getEmail()));
            }
            if (!StringUtils.hasText(order.getBuyerMobile()) && StringUtils.hasText(user.getMobile())) {
                order.setBuyerMobile(user.getMobile().trim());
            }
        });
    }

    private String resolveBuyerMobile(Long buyerUserId, String requestMobile, boolean persist) {
        User user = userRepository.findById(buyerUserId)
                .orElseThrow(() -> ApiException.unauthorized("Please sign in again"));
        String mobile = StringUtils.hasText(requestMobile) ? requestMobile.trim() : trimToNull(user.getMobile());
        if (!StringUtils.hasText(mobile)) {
            throw ApiException.badRequest("Mobile number is required to place an order");
        }
        if (persist && !mobile.equals(trimToNull(user.getMobile()))) {
            user.setMobile(mobile);
            userRepository.save(user);
        }
        return mobile;
    }

    static BillTotals computeBill(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal storeGstPercent) {
        BigDecimal sub = money(subtotal);
        BigDecimal discount = money(discountAmount).min(sub).max(BigDecimal.ZERO.setScale(2, MONEY));
        BigDecimal taxable = sub.subtract(discount).max(BigDecimal.ZERO.setScale(2, MONEY));
        BigDecimal gstPercent = storeGstPercent == null ? DEFAULT_GST : money(storeGstPercent);
        BigDecimal halfRate = gstPercent.divide(BigDecimal.valueOf(2), 6, MONEY);
        BigDecimal cgst = taxable.multiply(halfRate).divide(BigDecimal.valueOf(100), 2, MONEY);
        BigDecimal sgst = taxable.multiply(halfRate).divide(BigDecimal.valueOf(100), 2, MONEY);
        BigDecimal grand = taxable.add(cgst).add(sgst);
        return new BillTotals(sub, discount, taxable, cgst, sgst, gstPercent, grand);
    }

    private static void applyBill(WholesaleOrder order, BillTotals bill) {
        order.setSubtotal(bill.subtotal());
        order.setDiscountAmount(bill.discountAmount());
        order.setTaxableAmount(bill.taxableAmount());
        order.setCgstAmount(bill.cgstAmount());
        order.setSgstAmount(bill.sgstAmount());
        order.setGstPercent(bill.gstPercent());
        order.setGrandTotal(bill.grandTotal());
    }

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
        accessService.requireSupplier(userId);
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

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, MONEY);
        }
        return value.setScale(2, MONEY);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record PreparedCart(
            WholesaleStore store,
            BigDecimal subtotal,
            List<WholesaleOrderItem> items,
            Map<Long, Integer> bookedByProduct
    ) {}

    private record PreparedManualCart(
            BigDecimal subtotal,
            List<WholesaleOrderItem> items,
            Set<Long> productIds
    ) {}

    record BillTotals(
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxableAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal gstPercent,
            BigDecimal grandTotal
    ) {}
}
