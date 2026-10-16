package com.catalogstudio.inventory.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.inventory.dto.InventoryAdjustRequest;
import com.catalogstudio.inventory.dto.VariantCreateRequest;
import com.catalogstudio.inventory.dto.VariantResponse;
import com.catalogstudio.inventory.entity.InventoryLevel;
import com.catalogstudio.inventory.entity.InventoryMovement;
import com.catalogstudio.inventory.entity.InventoryMovement.MovementType;
import com.catalogstudio.inventory.entity.ProductVariant;
import com.catalogstudio.inventory.entity.ProductVariant.VariantStatus;
import com.catalogstudio.inventory.repository.InventoryLevelRepository;
import com.catalogstudio.inventory.repository.InventoryMovementRepository;
import com.catalogstudio.inventory.repository.ProductVariantRepository;
import com.catalogstudio.product.entity.Product;
import com.catalogstudio.product.repository.ProductRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;
    private final InventoryMovementRepository movementRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<VariantResponse> listInventory(Long userId) {
        return levelRepository.findAllForUser(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<VariantResponse> listVariantsForProduct(Long userId, UUID productId) {
        Product product = requireProduct(userId, productId);
        return variantRepository.findByProductIdOrderByCreatedAtAsc(product.getId()).stream()
                .map(v -> {
                    InventoryLevel level = levelRepository.findByVariantId(v.getId())
                            .orElseGet(() -> InventoryLevel.builder().variant(v).onHand(0).reserved(0).build());
                    return toResponse(v, level);
                })
                .toList();
    }

    @Transactional
    public VariantResponse createVariant(Long userId, UUID productId, VariantCreateRequest request) {
        Product product = requireProduct(userId, productId);
        String sku = request.sku().trim();
        if (variantRepository.existsByProductIdAndSkuIgnoreCase(product.getId(), sku)) {
            throw ApiException.conflict("Variant SKU already exists on this product");
        }
        ProductVariant variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku(sku)
                .sizeLabel(trimToNull(request.sizeLabel()))
                .colorLabel(trimToNull(request.colorLabel()))
                .barcode(trimToNull(request.barcode()))
                .price(request.price())
                .status(VariantStatus.ACTIVE)
                .build());

        int initial = request.initialOnHand() == null ? 0 : Math.max(0, request.initialOnHand());
        InventoryLevel level = levelRepository.save(InventoryLevel.builder()
                .variant(variant)
                .locationCode("DEFAULT")
                .onHand(initial)
                .reserved(0)
                .build());
        if (initial > 0) {
            recordMovement(userId, variant, level, MovementType.ADJUST, initial, "Initial stock");
        }
        return toResponse(variant, level);
    }

    @Transactional
    public VariantResponse adjust(Long userId, InventoryAdjustRequest request) {
        ProductVariant variant = variantRepository.findByUuidAndUserId(request.variantId(), userId)
                .orElseThrow(() -> ApiException.notFound("Variant not found"));
        InventoryLevel level = levelRepository.findByVariantId(variant.getId())
                .orElseGet(() -> levelRepository.save(InventoryLevel.builder()
                        .variant(variant)
                        .locationCode("DEFAULT")
                        .onHand(0)
                        .reserved(0)
                        .build()));
        int next = request.onHand();
        if (next < level.getReserved()) {
            throw ApiException.badRequest("onHand cannot be less than reserved (" + level.getReserved() + ")");
        }
        int delta = next - level.getOnHand();
        level.setOnHand(next);
        levelRepository.save(level);
        recordMovement(userId, variant, level, MovementType.ADJUST, delta, request.note());
        return toResponse(variant, level);
    }

    private void recordMovement(
            Long userId,
            ProductVariant variant,
            InventoryLevel level,
            MovementType type,
            int quantity,
            String note
    ) {
        User actor = userRepository.findById(userId).orElse(null);
        movementRepository.save(InventoryMovement.builder()
                .variant(variant)
                .movementType(type)
                .quantity(quantity)
                .onHandAfter(level.getOnHand())
                .reservedAfter(level.getReserved())
                .note(trimToNull(note))
                .createdBy(actor)
                .build());
    }

    private Product requireProduct(Long userId, UUID productId) {
        return productRepository.findByUuidAndUserId(productId, userId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private VariantResponse toResponse(InventoryLevel level) {
        return toResponse(level.getVariant(), level);
    }

    private VariantResponse toResponse(ProductVariant variant, InventoryLevel level) {
        Product product = variant.getProduct();
        return new VariantResponse(
                variant.getUuid(),
                product.getUuid(),
                product.getName(),
                variant.getSku(),
                variant.getSizeLabel(),
                variant.getColorLabel(),
                variant.getBarcode(),
                variant.getPrice(),
                variant.getStatus().name(),
                level.getOnHand(),
                level.getReserved(),
                level.available(),
                level.getUpdatedAt() == null ? variant.getUpdatedAt() : level.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
