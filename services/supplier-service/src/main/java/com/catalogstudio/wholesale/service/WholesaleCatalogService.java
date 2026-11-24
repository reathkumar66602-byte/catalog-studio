package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.wholesale.dto.CategoryResponse;
import com.catalogstudio.wholesale.dto.PageResponse;
import com.catalogstudio.wholesale.dto.ProductCardResponse;
import com.catalogstudio.wholesale.dto.ProductDetailResponse;
import com.catalogstudio.wholesale.dto.StoreResponse;
import com.catalogstudio.wholesale.entity.WholesaleCategory;
import com.catalogstudio.wholesale.entity.WholesaleProduct;
import com.catalogstudio.wholesale.entity.WholesaleStore;
import com.catalogstudio.wholesale.repository.WholesaleCategoryRepository;
import com.catalogstudio.wholesale.repository.WholesaleProductRepository;
import com.catalogstudio.wholesale.repository.WholesaleStoreRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Buyer-facing browse: categories, live-stock product grid, product detail, stores. */
@Service
@RequiredArgsConstructor
public class WholesaleCatalogService {

    private final WholesaleCategoryRepository categoryRepository;
    private final WholesaleProductRepository productRepository;
    private final WholesaleStoreRepository storeRepository;
    private final WholesaleMapper mapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categoryRepository.findByStatusOrderBySortOrderAscNameAsc("ACTIVE").stream()
                .map(mapper::category)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductCardResponse> products(
            String category, String store, String query, String sort, int page, int size) {
        Long categoryId = resolveCategoryId(category);
        Long storeId = resolveStoreId(store);
        Sort order = switch (sort == null ? "newest" : sort.trim().toLowerCase(Locale.ROOT)) {
            case "price_asc" -> Sort.by(Sort.Order.asc("minPrice"), Sort.Order.desc("id"));
            case "price_desc" -> Sort.by(Sort.Order.desc("maxPrice"), Sort.Order.desc("id"));
            case "popular" -> Sort.by(Sort.Order.desc("bookedCount"), Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), WholesaleProductService.clampSize(size), order);
        Page<WholesaleProduct> result = productRepository.findAll(
                browseSpec(categoryId, storeId, query), pageable);
        return PageResponse.of(result, mapper.productCards(result.getContent()));
    }

    /** Public detail by UUID or slug. DRAFT / ARCHIVED products are visible only to their owner. */
    @Transactional(readOnly = true)
    public ProductDetailResponse product(String idOrSlug, Long viewerUserId) {
        WholesaleProduct product = findProduct(idOrSlug);
        WholesaleStore store = storeRepository.findById(product.getStoreId())
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        boolean owner = viewerUserId != null && viewerUserId.equals(store.getOwnerUserId());
        boolean visible = product.getStatus() == WholesaleProduct.Status.ACTIVE
                && store.getStatus() == WholesaleStore.Status.ACTIVE;
        if (!visible && !owner) {
            throw ApiException.notFound("Product not found");
        }
        return mapper.productDetail(product, owner);
    }

    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> stores(String category, int page, int size) {
        Long categoryId = resolveCategoryId(category);
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), WholesaleProductService.clampSize(size),
                Sort.by(Sort.Order.desc("productCount"), Sort.Order.asc("id")));
        Page<WholesaleStore> result = categoryId == null
                ? storeRepository.findByStatus(WholesaleStore.Status.ACTIVE, pageable)
                : storeRepository.findActiveByCategory(categoryId, pageable);
        return PageResponse.of(result, mapper.stores(result.getContent(), false));
    }

    // --- helpers --------------------------------------------------------------------------

    private WholesaleProduct findProduct(String idOrSlug) {
        if (!StringUtils.hasText(idOrSlug)) {
            throw ApiException.notFound("Product not found");
        }
        String key = idOrSlug.trim();
        UUID uuid = parseUuid(key);
        return (uuid != null ? productRepository.findByUuid(uuid) : productRepository.findBySlug(key))
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private Long resolveCategoryId(String category) {
        if (!StringUtils.hasText(category) || "all".equalsIgnoreCase(category.trim())) {
            return null;
        }
        String key = category.trim();
        UUID uuid = parseUuid(key);
        WholesaleCategory found = (uuid != null
                ? categoryRepository.findByUuid(uuid)
                : categoryRepository.findBySlugIgnoreCase(key))
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        return found.getId();
    }

    private Long resolveStoreId(String store) {
        if (!StringUtils.hasText(store)) {
            return null;
        }
        String key = store.trim();
        UUID uuid = parseUuid(key);
        return (uuid != null ? storeRepository.findByUuid(uuid) : storeRepository.findBySlug(key))
                .map(WholesaleStore::getId)
                .orElseThrow(() -> ApiException.notFound("Store not found"));
    }

    private static Specification<WholesaleProduct> browseSpec(Long categoryId, Long storeId, String query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), WholesaleProduct.Status.ACTIVE));
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (storeId != null) {
                predicates.add(cb.equal(root.get("storeId"), storeId));
            }
            Subquery<Long> activeStore = cq.subquery(Long.class);
            Root<WholesaleStore> store = activeStore.from(WholesaleStore.class);
            activeStore.select(store.get("id")).where(
                    cb.equal(store.get("id"), root.get("storeId")),
                    cb.equal(store.get("status"), WholesaleStore.Status.ACTIVE));
            predicates.add(cb.exists(activeStore));
            if (StringUtils.hasText(query)) {
                String like = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("productCode")), like, '\\')));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
