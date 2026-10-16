package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.storage.StorageService;
import com.catalogstudio.wholesale.dto.PageResponse;
import com.catalogstudio.wholesale.dto.ProductCardResponse;
import com.catalogstudio.wholesale.dto.ProductCreateRequest;
import com.catalogstudio.wholesale.dto.ProductDetailResponse;
import com.catalogstudio.wholesale.dto.ProductImageResponse;
import com.catalogstudio.wholesale.dto.ProductUpdateRequest;
import com.catalogstudio.wholesale.dto.StockAdjustRequest;
import com.catalogstudio.wholesale.dto.VariantCell;
import com.catalogstudio.wholesale.dto.VariantInput;
import com.catalogstudio.wholesale.entity.WholesaleCategory;
import com.catalogstudio.wholesale.entity.WholesaleProduct;
import com.catalogstudio.wholesale.entity.WholesaleProductImage;
import com.catalogstudio.wholesale.entity.WholesaleStore;
import com.catalogstudio.wholesale.entity.WholesaleStoreCategory;
import com.catalogstudio.wholesale.entity.WholesaleVariant;
import com.catalogstudio.wholesale.repository.WholesaleCategoryRepository;
import com.catalogstudio.wholesale.repository.WholesaleProductImageRepository;
import com.catalogstudio.wholesale.repository.WholesaleProductRepository;
import com.catalogstudio.wholesale.repository.WholesaleStoreCategoryRepository;
import com.catalogstudio.wholesale.repository.WholesaleStoreRepository;
import com.catalogstudio.wholesale.repository.WholesaleVariantRepository;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** Supplier inventory: products, images, variants and stock. */
@Service
@RequiredArgsConstructor
public class WholesaleProductService {

    static final int MAX_IMAGES_PER_PRODUCT = 10;
    static final int MAX_VARIANTS_PER_PRODUCT = 200;
    static final String DEFAULT_COLOR = "Default";
    static final String DEFAULT_SIZE = "Free";

    private final WholesaleAccessService accessService;
    private final WholesaleStoreRepository storeRepository;
    private final WholesaleCategoryRepository categoryRepository;
    private final WholesaleStoreCategoryRepository storeCategoryRepository;
    private final WholesaleProductRepository productRepository;
    private final WholesaleProductImageRepository imageRepository;
    private final WholesaleVariantRepository variantRepository;
    private final WholesaleMapper mapper;
    private final StorageService storageService;

    // --- queries --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ProductCardResponse> listMyProducts(Long userId, String status, int page, int size) {
        accessService.requireSupplier(userId);
        WholesaleStore store = requireStore(userId);
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Page<WholesaleProduct> result;
        if (StringUtils.hasText(status)) {
            result = productRepository.findByStoreIdAndStatus(store.getId(), parseStatus(status), pageable);
        } else {
            result = productRepository.findByStoreId(store.getId(), pageable);
        }
        return PageResponse.of(result, mapper.productCards(result.getContent()));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getMyProduct(Long userId, UUID productId) {
        accessService.requireSupplier(userId);
        return mapper.productDetail(ownedProduct(userId, productId), true);
    }

    // --- product CRUD ---------------------------------------------------------------------

    @Transactional
    public ProductDetailResponse create(Long userId, ProductCreateRequest request) {
        accessService.requireSupplier(userId);
        WholesaleStore store = requireActiveStore(userId);
        WholesaleCategory category = resolveCategory(request.categoryId(), request.categorySlug());
        String code = request.code().trim();
        if (productRepository.existsByStoreIdAndProductCodeIgnoreCase(store.getId(), code)) {
            throw ApiException.conflict("Product code " + code + " already exists in your store");
        }
        List<WholesaleVariant> variants = buildVariants(code, request.variants(), request.mrp());

        WholesaleProduct product = WholesaleProduct.builder()
                .storeId(store.getId())
                .categoryId(category.getId())
                .title(request.title().trim())
                .slug(uniqueSlug(request.title(), code))
                .productCode(code)
                .description(trimToNull(request.description()))
                .detailsText(trimToNull(request.details()))
                .mrp(request.mrp())
                .status(WholesaleProduct.Status.DRAFT)
                .build();
        try {
            product = productRepository.saveAndFlush(product);
            for (WholesaleVariant variant : variants) {
                variant.setProductId(product.getId());
            }
            variantRepository.saveAllAndFlush(variants);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("Product could not be saved (duplicate code or SKU)");
        }
        ensureStoreCategory(store.getId(), category.getId());
        productRepository.refreshAggregates(product.getId());
        return mapper.productDetail(reload(product.getId()), true);
    }

    @Transactional
    public ProductDetailResponse update(Long userId, UUID productId, ProductUpdateRequest request) {
        accessService.requireSupplier(userId);
        WholesaleProduct product = ownedProduct(userId, productId);
        if (StringUtils.hasText(request.title())) {
            product.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            product.setDescription(trimToNull(request.description()));
        }
        if (request.details() != null) {
            product.setDetailsText(trimToNull(request.details()));
        }
        if (request.mrp() != null) {
            product.setMrp(request.mrp());
        }
        if (request.categoryId() != null || StringUtils.hasText(request.categorySlug())) {
            WholesaleCategory category = resolveCategory(request.categoryId(), request.categorySlug());
            if (!category.getId().equals(product.getCategoryId())) {
                if (product.getStatus() == WholesaleProduct.Status.ACTIVE) {
                    categoryRepository.adjustProductCount(product.getCategoryId(), -1);
                    categoryRepository.adjustProductCount(category.getId(), 1);
                }
                product.setCategoryId(category.getId());
                ensureStoreCategory(product.getStoreId(), category.getId());
            }
        }
        productRepository.saveAndFlush(product);
        return mapper.productDetail(reload(product.getId()), true);
    }

    @Transactional
    public ProductDetailResponse publish(Long userId, UUID productId) {
        accessService.requireSupplier(userId);
        WholesaleProduct product = ownedProduct(userId, productId);
        WholesaleStore store = requireActiveStore(userId);
        if (product.getStatus() == WholesaleProduct.Status.ACTIVE) {
            return mapper.productDetail(product, true);
        }
        if (imageRepository.countByProductId(product.getId()) == 0) {
            throw ApiException.badRequest("Add at least one photo before publishing");
        }
        List<WholesaleVariant> variants = variantRepository.findByProductIdAndStatus(
                product.getId(), WholesaleVariant.Status.ACTIVE);
        if (variants.isEmpty()) {
            throw ApiException.badRequest("Add at least one active colour/size variant before publishing");
        }
        product.setStatus(WholesaleProduct.Status.ACTIVE);
        productRepository.saveAndFlush(product);
        categoryRepository.adjustProductCount(product.getCategoryId(), 1);
        storeRepository.adjustProductCount(store.getId(), 1);
        productRepository.refreshAggregates(product.getId());
        return mapper.productDetail(reload(product.getId()), true);
    }

    @Transactional
    public ProductDetailResponse archive(Long userId, UUID productId) {
        accessService.requireSupplier(userId);
        WholesaleProduct product = ownedProduct(userId, productId);
        if (product.getStatus() == WholesaleProduct.Status.ARCHIVED) {
            return mapper.productDetail(product, true);
        }
        boolean wasActive = product.getStatus() == WholesaleProduct.Status.ACTIVE;
        product.setStatus(WholesaleProduct.Status.ARCHIVED);
        productRepository.saveAndFlush(product);
        if (wasActive) {
            categoryRepository.adjustProductCount(product.getCategoryId(), -1);
            storeRepository.adjustProductCount(product.getStoreId(), -1);
        }
        return mapper.productDetail(reload(product.getId()), true);
    }

    // --- images ---------------------------------------------------------------------------

    @Transactional
    public ProductImageResponse addImage(Long userId, UUID productId, MultipartFile file, String colorLabel) {
        accessService.requireSupplier(userId);
        WholesaleProduct product = ownedProduct(userId, productId);
        long existing = imageRepository.countByProductId(product.getId());
        if (existing >= MAX_IMAGES_PER_PRODUCT) {
            throw ApiException.badRequest("A product can have up to " + MAX_IMAGES_PER_PRODUCT + " photos");
        }
        StorageService.StoredFile stored = storageService.store(file);
        WholesaleProductImage image = imageRepository.save(WholesaleProductImage.builder()
                .productId(product.getId())
                .storageKey(stored.storageKey())
                .publicUrl(stored.publicUrl())
                .sortOrder((int) existing)
                .colorLabel(trimToNull(colorLabel))
                .build());
        if (!StringUtils.hasText(product.getPrimaryImageUrl())) {
            product.setPrimaryImageUrl(stored.publicUrl());
            productRepository.save(product);
        }
        return new ProductImageResponse(image.getUuid(), image.getPublicUrl(), image.getColorLabel(), image.getSortOrder());
    }

    @Transactional
    public void deleteImage(Long userId, UUID productId, UUID imageId) {
        accessService.requireSupplier(userId);
        WholesaleProduct product = ownedProduct(userId, productId);
        WholesaleProductImage image = imageRepository.findByUuidAndProductId(imageId, product.getId())
                .orElseThrow(() -> ApiException.notFound("Image not found"));
        if (product.getStatus() == WholesaleProduct.Status.ACTIVE
                && imageRepository.countByProductId(product.getId()) <= 1) {
            throw ApiException.badRequest("A published product needs at least one photo");
        }
        imageRepository.delete(image);
        imageRepository.flush();
        List<WholesaleProductImage> remaining = imageRepository.findByProductIdOrderBySortOrderAscIdAsc(product.getId());
        product.setPrimaryImageUrl(remaining.isEmpty() ? null : remaining.get(0).getPublicUrl());
        productRepository.save(product);
        try {
            storageService.delete(image.getStorageKey());
        } catch (RuntimeException ignored) {
            // Orphaned file is harmless; DB row is already gone.
        }
    }

    // --- variants & stock -----------------------------------------------------------------

    @Transactional
    public VariantCell addVariant(Long userId, UUID productId, VariantInput input) {
        accessService.requireSupplier(userId);
        WholesaleProduct product = ownedProduct(userId, productId);
        if (variantRepository.findByProductIdOrderByColorLabelAscSizeLabelAsc(product.getId()).size()
                >= MAX_VARIANTS_PER_PRODUCT) {
            throw ApiException.badRequest("Too many variants for one product");
        }
        WholesaleVariant variant = buildVariants(product.getProductCode(), List.of(input), product.getMrp()).get(0);
        variant.setProductId(product.getId());
        try {
            variant = variantRepository.saveAndFlush(variant);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("That colour/size or SKU already exists for this product");
        }
        productRepository.refreshAggregates(product.getId());
        return mapper.variantCell(variant, true);
    }

    @Transactional
    public VariantCell adjustStock(Long userId, UUID variantId, StockAdjustRequest request) {
        accessService.requireSupplier(userId);
        if (request.onHand() == null && request.delta() == null && request.price() == null) {
            throw ApiException.badRequest("Send onHand, delta or price");
        }
        WholesaleVariant variant = variantRepository.findByUuid(variantId)
                .orElseThrow(() -> ApiException.notFound("Variant not found"));
        ownedProduct(userId, variant.getProductId());

        if (request.onHand() != null || request.delta() != null) {
            long target = request.onHand() != null
                    ? request.onHand()
                    : (long) variant.getOnHand() + request.delta();
            if (target < 0 || target > 10_000_000L) {
                throw ApiException.badRequest("Stock must be between 0 and 10,000,000");
            }
            if (target < variant.getReserved()) {
                throw ApiException.badRequest(
                        "Stock cannot go below " + variant.getReserved() + " pcs already reserved by open orders");
            }
            variant.setOnHand((int) target);
        }
        if (request.price() != null) {
            variant.setPrice(request.price());
        }
        try {
            variant = variantRepository.saveAndFlush(variant);
        } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
            throw ApiException.conflict("Stock was just updated by another request. Please retry.");
        }
        productRepository.refreshAggregates(variant.getProductId());
        return mapper.variantCell(variant, true);
    }

    // --- helpers --------------------------------------------------------------------------

    private WholesaleStore requireStore(Long userId) {
        return storeRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Create your store first"));
    }

    private WholesaleStore requireActiveStore(Long userId) {
        WholesaleStore store = requireStore(userId);
        if (store.getStatus() != WholesaleStore.Status.ACTIVE) {
            throw ApiException.forbidden("Your store is not active. Complete GST verification or contact support.");
        }
        return store;
    }

    private WholesaleProduct ownedProduct(Long userId, UUID productId) {
        WholesaleProduct product = productRepository.findByUuid(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        return assertOwner(userId, product);
    }

    private WholesaleProduct ownedProduct(Long userId, Long productDbId) {
        WholesaleProduct product = productRepository.findById(productDbId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        return assertOwner(userId, product);
    }

    private WholesaleProduct assertOwner(Long userId, WholesaleProduct product) {
        WholesaleStore store = requireStore(userId);
        if (!store.getId().equals(product.getStoreId())) {
            // Do not reveal that the product exists.
            throw ApiException.notFound("Product not found");
        }
        return product;
    }

    private WholesaleProduct reload(Long id) {
        return productRepository.findById(id).orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private WholesaleCategory resolveCategory(UUID id, String slug) {
        WholesaleCategory category;
        if (id != null) {
            category = categoryRepository.findByUuid(id).orElse(null);
        } else if (StringUtils.hasText(slug)) {
            category = categoryRepository.findBySlugIgnoreCase(slug.trim()).orElse(null);
        } else {
            throw ApiException.badRequest("Select a category");
        }
        if (category == null || !"ACTIVE".equalsIgnoreCase(category.getStatus())) {
            throw ApiException.badRequest("Category not found");
        }
        return category;
    }

    private void ensureStoreCategory(Long storeId, Long categoryId) {
        if (!storeCategoryRepository.existsByStoreIdAndCategoryId(storeId, categoryId)) {
            storeCategoryRepository.save(WholesaleStoreCategory.builder()
                    .storeId(storeId).categoryId(categoryId).build());
        }
    }

    private List<WholesaleVariant> buildVariants(String productCode, List<VariantInput> inputs, BigDecimal productMrp) {
        if (inputs == null || inputs.isEmpty()) {
            throw ApiException.badRequest("Add at least one colour/size variant");
        }
        if (inputs.size() > MAX_VARIANTS_PER_PRODUCT) {
            throw ApiException.badRequest("Too many variants (max " + MAX_VARIANTS_PER_PRODUCT + ")");
        }
        Set<String> combos = new HashSet<>();
        Set<String> skus = new HashSet<>();
        List<WholesaleVariant> result = new ArrayList<>(inputs.size());
        for (VariantInput input : inputs) {
            String color = StringUtils.hasText(input.colorLabel()) ? input.colorLabel().trim() : DEFAULT_COLOR;
            String size = StringUtils.hasText(input.sizeLabel()) ? input.sizeLabel().trim() : DEFAULT_SIZE;
            String combo = color.toLowerCase(Locale.ROOT) + "|" + size.toLowerCase(Locale.ROOT);
            if (!combos.add(combo)) {
                throw ApiException.badRequest("Duplicate variant: " + color + " / " + size);
            }
            String sku = StringUtils.hasText(input.sku())
                    ? input.sku().trim()
                    : (productCode + "-" + WholesaleSlugs.slugify(color, 20) + "-" + WholesaleSlugs.slugify(size, 12))
                    .toUpperCase(Locale.ROOT);
            if (!skus.add(sku.toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("Duplicate SKU: " + sku);
            }
            result.add(WholesaleVariant.builder()
                    .productId(0L) // set by caller
                    .sku(sku)
                    .colorLabel(color)
                    .sizeLabel(size)
                    .colorHex(normalizeHex(input.colorHex()))
                    .price(input.price())
                    .mrp(input.mrp() != null ? input.mrp() : productMrp)
                    .onHand(Math.max(0, input.stock()))
                    .reserved(0)
                    .status(WholesaleVariant.Status.ACTIVE)
                    .build());
        }
        return result;
    }

    private static String normalizeHex(String hex) {
        if (!StringUtils.hasText(hex)) {
            return null;
        }
        String value = hex.trim();
        return value.startsWith("#") ? value : "#" + value;
    }

    private String uniqueSlug(String title, String code) {
        String base = WholesaleSlugs.slugify(title + " " + code, 240);
        if (base.isEmpty()) {
            base = "product";
        }
        String slug = base + "-" + WholesaleSlugs.randomSuffix(6);
        while (productRepository.existsBySlug(slug)) {
            slug = base + "-" + WholesaleSlugs.randomSuffix(6);
        }
        return slug;
    }

    private static WholesaleProduct.Status parseStatus(String status) {
        try {
            return WholesaleProduct.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown status: " + status);
        }
    }

    static int clampSize(int size) {
        return size <= 0 ? 24 : Math.min(size, 60);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
