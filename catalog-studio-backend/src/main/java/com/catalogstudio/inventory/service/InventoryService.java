package com.catalogstudio.inventory.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.inventory.dto.InventoryAdjustRequest;
import com.catalogstudio.inventory.dto.VariantCreateRequest;
import com.catalogstudio.inventory.dto.VariantResponse;
import com.catalogstudio.inventory.dto.WholesaleReceiptCreditRequest;
import com.catalogstudio.inventory.dto.WholesaleReceiptCreditResult;
import com.catalogstudio.inventory.entity.InventoryLevel;
import com.catalogstudio.inventory.entity.InventoryMovement;
import com.catalogstudio.inventory.entity.InventoryMovement.MovementType;
import com.catalogstudio.inventory.entity.ProductVariant;
import com.catalogstudio.inventory.entity.ProductVariant.VariantStatus;
import com.catalogstudio.inventory.repository.InventoryLevelRepository;
import com.catalogstudio.inventory.repository.InventoryMovementRepository;
import com.catalogstudio.inventory.repository.ProductVariantRepository;
import com.catalogstudio.product.entity.Product;
import com.catalogstudio.product.entity.Product.ProductStatus;
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
            recordMovement(userId, variant, level, MovementType.ADJUST, initial, null, "Initial stock");
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
        recordMovement(userId, variant, level, MovementType.ADJUST, delta, null, request.note());
        return toResponse(variant, level);
    }

    /**
     * Credits only the accepted quantity from a wholesale goods receipt.
     * An existing retailer product keeps its title, description, and price.
     * The same receipt line is applied at most once.
     */
    @Transactional
    public WholesaleReceiptCreditResult applyWholesaleReceipt(WholesaleReceiptCreditRequest request) {
        if (request.buyerUserId() == null || request.receiptId() == null) {
            throw ApiException.badRequest("buyerUserId and receiptId are required");
        }
        User buyer = userRepository.findById(request.buyerUserId())
                .orElseThrow(() -> ApiException.notFound("Retailer not found"));
        int credited = 0;
        List<WholesaleReceiptCreditRequest.Line> lines = request.lines() == null ? List.of() : request.lines();
        for (WholesaleReceiptCreditRequest.Line line : lines) {
            if (line.lineId() == null) {
                throw ApiException.badRequest("Each receipt line needs an id");
            }
            if (line.acceptedQty() <= 0) {
                continue;
            }
            String reference = "GR:" + request.receiptId() + ":" + line.lineId();
            if (movementRepository.existsByReference(reference)) {
                continue;
            }
            ProductVariant variant = resolveReceiptVariant(buyer, line);
            InventoryLevel level = levelRepository.findByVariantId(variant.getId())
                    .orElseGet(() -> levelRepository.save(InventoryLevel.builder()
                            .variant(variant)
                            .locationCode("DEFAULT")
                            .onHand(0)
                            .reserved(0)
                            .build()));
            level = levelRepository.lockById(level.getId())
                    .orElseThrow(() -> ApiException.conflict("Inventory row is not available"));
            if (movementRepository.existsByReference(reference)) {
                continue;
            }
            int next;
            try {
                next = Math.addExact(level.getOnHand(), line.acceptedQty());
            } catch (ArithmeticException ex) {
                throw ApiException.badRequest("Accepted quantity is too large");
            }
            level.setOnHand(next);
            levelRepository.save(level);
            recordMovement(
                    buyer.getId(),
                    variant,
                    level,
                    MovementType.RECEIPT,
                    line.acceptedQty(),
                    reference,
                    "Wholesale goods receipt " + request.receiptId());
            credited += line.acceptedQty();
        }
        return new WholesaleReceiptCreditResult(request.receiptId(), credited);
    }

    private ProductVariant resolveReceiptVariant(User buyer, WholesaleReceiptCreditRequest.Line line) {
        String sku = line.sku() == null ? "" : line.sku().trim();
        if (sku.isEmpty() || sku.length() > 80) {
            throw ApiException.badRequest("Accepted stock needs a SKU of 1 to 80 characters");
        }
        List<ProductVariant> matches = variantRepository.findAllByUserIdAndSkuIgnoreCase(buyer.getId(), sku);
        if (matches.size() > 1) {
            throw ApiException.conflict("Multiple catalog variants use SKU " + sku);
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        Product product = productRepository.findByUserIdAndSkuIgnoreCase(buyer.getId(), sku).orElse(null);
        if (product == null) {
            String title = line.title() == null || line.title().isBlank() ? sku : line.title().trim();
            if (title.length() > 255) {
                title = title.substring(0, 255);
            }
            product = productRepository.save(Product.builder()
                    .user(buyer)
                    .name(title)
                    .sku(sku)
                    .basePrice(line.unitCost())
                    .description("Imported from a wholesale goods receipt. This retailer copy is not overwritten by later supplier edits.")
                    .status(ProductStatus.ACTIVE)
                    .build());
        }
        return variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku(sku)
                .sizeLabel(trimLimit(line.sizeLabel(), 40))
                .colorLabel(trimLimit(line.colorLabel(), 80))
                .price(line.unitCost())
                .status(VariantStatus.ACTIVE)
                .build());
    }

    private void recordMovement(
            Long userId,
            ProductVariant variant,
            InventoryLevel level,
            MovementType type,
            int quantity,
            String reference,
            String note
    ) {
        User actor = userRepository.findById(userId).orElse(null);
        movementRepository.save(InventoryMovement.builder()
                .variant(variant)
                .movementType(type)
                .quantity(quantity)
                .onHandAfter(level.getOnHand())
                .reservedAfter(level.getReserved())
                .reference(reference)
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

    private static String trimLimit(String value, int max) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }
}
