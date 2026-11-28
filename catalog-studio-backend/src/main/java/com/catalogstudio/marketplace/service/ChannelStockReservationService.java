package com.catalogstudio.marketplace.service;

import com.catalogstudio.inventory.entity.InventoryLevel;
import com.catalogstudio.inventory.entity.InventoryMovement;
import com.catalogstudio.inventory.entity.InventoryMovement.MovementType;
import com.catalogstudio.inventory.entity.ProductVariant;
import com.catalogstudio.inventory.repository.InventoryLevelRepository;
import com.catalogstudio.inventory.repository.InventoryMovementRepository;
import com.catalogstudio.inventory.repository.ProductVariantRepository;
import com.catalogstudio.marketplace.entity.MarketplaceChannelOrder;
import com.catalogstudio.marketplace.repository.MarketplaceChannelOrderRepository;
import com.catalogstudio.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Base-style warehouse reservation: import order → reserve; cancel → release; ship → sale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelStockReservationService {

    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;
    private final InventoryMovementRepository movementRepository;
    private final MarketplaceChannelOrderRepository orderRepository;
    private final UserRepository userRepository;

    @Transactional
    public boolean reserveForOrder(Long userId, MarketplaceChannelOrder order) {
        if (order == null || order.isInventoryReserved()) {
            return false;
        }
        String ref = reserveRef(order);
        if (movementRepository.existsByReference(ref)) {
            order.setInventoryReserved(true);
            orderRepository.save(order);
            return false;
        }
        ProductVariant variant = findVariant(userId, order.getSku());
        if (variant == null) {
            log.info("No CS variant for marketplace SKU {} — skip reserve", order.getSku());
            return false;
        }
        InventoryLevel level = levelRepository.findByVariantId(variant.getId())
                .orElseGet(() -> levelRepository.save(InventoryLevel.builder()
                        .variant(variant)
                        .locationCode("DEFAULT")
                        .onHand(0)
                        .reserved(0)
                        .build()));
        level = levelRepository.lockById(level.getId()).orElse(level);
        int qty = Math.max(1, order.getQuantity() == null ? 1 : order.getQuantity());
        int available = Math.max(0, level.getOnHand() - level.getReserved());
        int reserveQty = Math.min(qty, available);
        if (reserveQty <= 0) {
            log.warn("Cannot reserve {} for order {} — available {}", qty, order.getExternalOrderId(), available);
            return false;
        }
        level.setReserved(level.getReserved() + reserveQty);
        levelRepository.save(level);
        movementRepository.save(InventoryMovement.builder()
                .variant(variant)
                .movementType(MovementType.RESERVE)
                .quantity(reserveQty)
                .onHandAfter(level.getOnHand())
                .reservedAfter(level.getReserved())
                .reference(ref)
                .note(order.getMarketplace() + " order " + order.getExternalOrderId())
                .createdBy(userRepository.getReferenceById(userId))
                .build());
        order.setInventoryReserved(true);
        orderRepository.save(order);
        return true;
    }

    @Transactional
    public boolean releaseForOrder(Long userId, MarketplaceChannelOrder order) {
        if (order == null || !order.isInventoryReserved()) {
            return false;
        }
        String releaseRef = releaseRef(order);
        if (movementRepository.existsByReference(releaseRef)) {
            order.setInventoryReserved(false);
            orderRepository.save(order);
            return false;
        }
        ProductVariant variant = findVariant(userId, order.getSku());
        if (variant == null) {
            order.setInventoryReserved(false);
            orderRepository.save(order);
            return false;
        }
        InventoryLevel level = levelRepository.findByVariantId(variant.getId()).orElse(null);
        if (level == null) {
            order.setInventoryReserved(false);
            orderRepository.save(order);
            return false;
        }
        level = levelRepository.lockById(level.getId()).orElse(level);
        int qty = Math.max(1, order.getQuantity() == null ? 1 : order.getQuantity());
        int releaseQty = Math.min(qty, level.getReserved());
        level.setReserved(Math.max(0, level.getReserved() - releaseQty));
        levelRepository.save(level);
        movementRepository.save(InventoryMovement.builder()
                .variant(variant)
                .movementType(MovementType.RELEASE)
                .quantity(releaseQty)
                .onHandAfter(level.getOnHand())
                .reservedAfter(level.getReserved())
                .reference(releaseRef)
                .note("Release " + order.getMarketplace() + " " + order.getExternalOrderId())
                .createdBy(userRepository.getReferenceById(userId))
                .build());
        order.setInventoryReserved(false);
        orderRepository.save(order);
        return true;
    }

    @Transactional
    public boolean commitSaleForOrder(Long userId, MarketplaceChannelOrder order) {
        if (order == null) {
            return false;
        }
        String saleRef = saleRef(order);
        if (movementRepository.existsByReference(saleRef)) {
            return false;
        }
        ProductVariant variant = findVariant(userId, order.getSku());
        if (variant == null) {
            return false;
        }
        InventoryLevel level = levelRepository.findByVariantId(variant.getId()).orElse(null);
        if (level == null) {
            return false;
        }
        level = levelRepository.lockById(level.getId()).orElse(level);
        int qty = Math.max(1, order.getQuantity() == null ? 1 : order.getQuantity());
        int fromReserved = Math.min(qty, level.getReserved());
        level.setReserved(Math.max(0, level.getReserved() - fromReserved));
        int deduct = Math.min(qty, level.getOnHand());
        level.setOnHand(Math.max(0, level.getOnHand() - deduct));
        levelRepository.save(level);
        movementRepository.save(InventoryMovement.builder()
                .variant(variant)
                .movementType(MovementType.SALE)
                .quantity(deduct)
                .onHandAfter(level.getOnHand())
                .reservedAfter(level.getReserved())
                .reference(saleRef)
                .note("Sale " + order.getMarketplace() + " " + order.getExternalOrderId())
                .createdBy(userRepository.getReferenceById(userId))
                .build());
        order.setInventoryReserved(false);
        orderRepository.save(order);
        return true;
    }

    @Transactional
    public boolean restockReturn(Long userId, MarketplaceChannelOrder order) {
        if (order == null || !StringUtils.hasText(order.getSku())) {
            return false;
        }
        String ref = "OMS-RETURN:" + order.getUuid();
        if (movementRepository.existsByReference(ref)) {
            return false;
        }
        ProductVariant variant = findVariant(userId, order.getSku());
        if (variant == null) {
            return false;
        }
        InventoryLevel level = levelRepository.findByVariantId(variant.getId())
                .orElseGet(() -> levelRepository.save(InventoryLevel.builder()
                        .variant(variant)
                        .locationCode("DEFAULT")
                        .onHand(0)
                        .reserved(0)
                        .build()));
        level = levelRepository.lockById(level.getId()).orElse(level);
        int qty = Math.max(1, order.getQuantity() == null ? 1 : order.getQuantity());
        level.setOnHand(level.getOnHand() + qty);
        levelRepository.save(level);
        movementRepository.save(InventoryMovement.builder()
                .variant(variant)
                .movementType(MovementType.RETURN)
                .quantity(qty)
                .onHandAfter(level.getOnHand())
                .reservedAfter(level.getReserved())
                .reference(ref)
                .note("Return " + order.getMarketplace() + " " + order.getExternalOrderId())
                .createdBy(userRepository.getReferenceById(userId))
                .build());
        return true;
    }

    private ProductVariant findVariant(Long userId, String sku) {
        if (!StringUtils.hasText(sku)) {
            return null;
        }
        List<ProductVariant> matches = variantRepository.findAllByUserIdAndSkuIgnoreCase(userId, sku.trim());
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            log.warn("Multiple variants for SKU {} user {} — using first", sku, userId);
        }
        return matches.get(0);
    }

    private static String reserveRef(MarketplaceChannelOrder order) {
        return "OMS-RESERVE:" + order.getUuid();
    }

    private static String releaseRef(MarketplaceChannelOrder order) {
        return "OMS-RELEASE:" + order.getUuid();
    }

    private static String saleRef(MarketplaceChannelOrder order) {
        return "OMS-SALE:" + order.getUuid();
    }

    public static boolean looksCancelled(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String s = status.toLowerCase(Locale.ROOT);
        return s.contains("cancel");
    }
}
