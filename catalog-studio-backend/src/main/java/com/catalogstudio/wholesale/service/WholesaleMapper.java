package com.catalogstudio.wholesale.service;

import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import com.catalogstudio.wholesale.dto.CategoryResponse;
import com.catalogstudio.wholesale.dto.ColorSwatch;
import com.catalogstudio.wholesale.dto.OrderResponse;
import com.catalogstudio.wholesale.dto.ProductCardResponse;
import com.catalogstudio.wholesale.dto.ProductDetailResponse;
import com.catalogstudio.wholesale.dto.ProductImageResponse;
import com.catalogstudio.wholesale.dto.StoreResponse;
import com.catalogstudio.wholesale.dto.VariantCell;
import com.catalogstudio.wholesale.entity.WholesaleCategory;
import com.catalogstudio.wholesale.entity.WholesaleOrder;
import com.catalogstudio.wholesale.entity.WholesaleOrderItem;
import com.catalogstudio.wholesale.entity.WholesaleProduct;
import com.catalogstudio.wholesale.entity.WholesaleProductImage;
import com.catalogstudio.wholesale.entity.WholesaleStore;
import com.catalogstudio.wholesale.entity.WholesaleStoreCategory;
import com.catalogstudio.wholesale.entity.WholesaleVariant;
import com.catalogstudio.wholesale.repository.WholesaleCategoryRepository;
import com.catalogstudio.wholesale.repository.WholesaleOrderItemRepository;
import com.catalogstudio.wholesale.repository.WholesaleProductImageRepository;
import com.catalogstudio.wholesale.repository.WholesaleProductRepository;
import com.catalogstudio.wholesale.repository.WholesaleStoreCategoryRepository;
import com.catalogstudio.wholesale.repository.WholesaleStoreRepository;
import com.catalogstudio.wholesale.repository.WholesaleVariantRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Entity -> DTO mapping with batched lookups (open-in-view is off; no lazy associations are used).
 * Call from inside a service transaction.
 */
@Component
@RequiredArgsConstructor
public class WholesaleMapper {

    private final WholesaleCategoryRepository categoryRepository;
    private final WholesaleStoreRepository storeRepository;
    private final WholesaleProductRepository productRepository;
    private final WholesaleStoreCategoryRepository storeCategoryRepository;
    private final WholesaleProductImageRepository imageRepository;
    private final WholesaleVariantRepository variantRepository;
    private final WholesaleOrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

    public CategoryResponse category(WholesaleCategory category) {
        return new CategoryResponse(category.getUuid(), category.getName(), category.getSlug(), category.getProductCount());
    }

    // --- stores ---------------------------------------------------------------------------

    public StoreResponse store(WholesaleStore store, boolean ownerView) {
        return stores(List.of(store), ownerView).get(0);
    }

    public List<StoreResponse> stores(List<WholesaleStore> stores, boolean ownerView) {
        if (stores.isEmpty()) {
            return List.of();
        }
        List<Long> storeIds = stores.stream().map(WholesaleStore::getId).toList();
        Map<Long, List<Long>> categoryIdsByStore = new HashMap<>();
        Set<Long> allCategoryIds = new LinkedHashSet<>();
        for (WholesaleStoreCategory link : storeCategoryRepository.findByStoreIdIn(storeIds)) {
            categoryIdsByStore.computeIfAbsent(link.getStoreId(), k -> new ArrayList<>()).add(link.getCategoryId());
            allCategoryIds.add(link.getCategoryId());
        }
        Map<Long, WholesaleCategory> categories = categoryRepository.findAllById(allCategoryIds).stream()
                .collect(Collectors.toMap(WholesaleCategory::getId, c -> c));
        List<StoreResponse> result = new ArrayList<>(stores.size());
        for (WholesaleStore store : stores) {
            List<CategoryResponse> storeCategories = categoryIdsByStore.getOrDefault(store.getId(), List.of()).stream()
                    .map(categories::get)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparingInt(WholesaleCategory::getSortOrder))
                    .map(this::category)
                    .toList();
            result.add(new StoreResponse(
                    store.getUuid(),
                    store.getStoreName(),
                    store.getSlug(),
                    store.getDescription(),
                    store.getCity(),
                    store.getState(),
                    store.getStatus().name(),
                    store.getProductCount(),
                    storeCategories,
                    ownerView ? store.getGstin() : null,
                    ownerView ? store.getGstLegalName() : null,
                    ownerView ? store.getGstVerificationStatus() : null,
                    ownerView ? store.getGstVerifiedAt() : null,
                    ownerView ? store.getPhone() : null,
                    ownerView ? store.getEmail() : null));
        }
        return result;
    }

    // --- products -------------------------------------------------------------------------

    public List<ProductCardResponse> productCards(List<WholesaleProduct> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(WholesaleProduct::getId).toList();
        Map<Long, WholesaleStore> stores = storeRepository
                .findByIdIn(products.stream().map(WholesaleProduct::getStoreId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(WholesaleStore::getId, s -> s));
        Map<Long, WholesaleCategory> categories = categoryRepository
                .findAllById(products.stream().map(WholesaleProduct::getCategoryId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(WholesaleCategory::getId, c -> c));
        Map<Long, List<ColorSwatch>> colors = new HashMap<>();
        for (Object[] row : variantRepository.findColorsByProductIds(productIds)) {
            Long productId = (Long) row[0];
            colors.computeIfAbsent(productId, k -> new ArrayList<>())
                    .add(new ColorSwatch((String) row[1], (String) row[2]));
        }
        List<ProductCardResponse> result = new ArrayList<>(products.size());
        for (WholesaleProduct product : products) {
            WholesaleStore store = stores.get(product.getStoreId());
            WholesaleCategory category = categories.get(product.getCategoryId());
            List<ColorSwatch> productColors = colors.getOrDefault(product.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(ColorSwatch::label, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            result.add(new ProductCardResponse(
                    product.getUuid(),
                    product.getSlug(),
                    product.getTitle(),
                    product.getProductCode(),
                    product.getStatus().name(),
                    product.getPrimaryImageUrl(),
                    product.getMinPrice(),
                    product.getMaxPrice(),
                    product.getMrp(),
                    productColors,
                    product.getTotalStock(),
                    product.getBookedCount(),
                    category == null ? null : category.getName(),
                    category == null ? null : category.getSlug(),
                    store == null ? null : store.getUuid(),
                    store == null ? null : store.getStoreName(),
                    store == null ? null : store.getSlug()));
        }
        return result;
    }

    public ProductDetailResponse productDetail(WholesaleProduct product, boolean ownerView) {
        WholesaleStore store = storeRepository.findById(product.getStoreId()).orElseThrow();
        WholesaleCategory category = categoryRepository.findById(product.getCategoryId()).orElseThrow();
        List<WholesaleProductImage> images = imageRepository.findByProductIdOrderBySortOrderAscIdAsc(product.getId());
        List<WholesaleVariant> variants = variantRepository.findByProductIdOrderByColorLabelAscSizeLabelAsc(product.getId());
        if (!ownerView) {
            variants = variants.stream().filter(v -> v.getStatus() == WholesaleVariant.Status.ACTIVE).toList();
        }

        Map<String, ColorSwatch> colors = new LinkedHashMap<>();
        Set<String> sizes = new LinkedHashSet<>();
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
        List<VariantCell> cells = new ArrayList<>(variants.size());
        for (WholesaleVariant v : variants) {
            colors.putIfAbsent(v.getColorLabel().toLowerCase(), new ColorSwatch(v.getColorLabel(), v.getColorHex()));
            sizes.add(v.getSizeLabel());
            matrix.computeIfAbsent(v.getColorLabel(), k -> new LinkedHashMap<>()).put(v.getSizeLabel(), v.available());
            cells.add(variantCell(v, ownerView));
        }
        List<String> orderedSizes = sortSizes(sizes);
        // Keep matrix rows in size order so tables render consistently.
        Map<String, Map<String, Integer>> orderedMatrix = new LinkedHashMap<>();
        matrix.forEach((color, bySize) -> {
            Map<String, Integer> row = new LinkedHashMap<>();
            for (String size : orderedSizes) {
                if (bySize.containsKey(size)) {
                    row.put(size, bySize.get(size));
                }
            }
            orderedMatrix.put(color, row);
        });

        return new ProductDetailResponse(
                product.getUuid(),
                product.getSlug(),
                product.getTitle(),
                product.getProductCode(),
                product.getStatus().name(),
                product.getDescription(),
                product.getDetailsText(),
                product.getMrp(),
                product.getMinPrice(),
                product.getMaxPrice(),
                product.getTotalStock(),
                product.getBookedCount(),
                category(category),
                new ProductDetailResponse.StoreSummary(
                        store.getUuid(), store.getStoreName(), store.getSlug(), store.getCity(), store.getState()),
                images.stream()
                        .map(i -> new ProductImageResponse(i.getUuid(), i.getPublicUrl(), i.getColorLabel(), i.getSortOrder()))
                        .toList(),
                new ArrayList<>(colors.values()),
                orderedSizes,
                orderedMatrix,
                cells);
    }

    public VariantCell variantCell(WholesaleVariant v, boolean ownerView) {
        return new VariantCell(
                v.getUuid(),
                v.getSku(),
                v.getColorLabel(),
                v.getSizeLabel(),
                v.getColorHex(),
                v.getPrice(),
                v.getMrp(),
                v.available(),
                ownerView ? v.getOnHand() : null,
                ownerView ? v.getReserved() : null,
                v.getStatus().name());
    }

    private static final List<String> SIZE_ORDER = List.of(
            "XXS", "XS", "S", "M", "L", "XL", "XXL", "2XL", "XXXL", "3XL", "4XL", "5XL", "FREE", "FREE SIZE");

    static List<String> sortSizes(Collection<String> sizes) {
        return sizes.stream().sorted((a, b) -> {
            int ia = SIZE_ORDER.indexOf(a.trim().toUpperCase());
            int ib = SIZE_ORDER.indexOf(b.trim().toUpperCase());
            if (ia >= 0 && ib >= 0) {
                return Integer.compare(ia, ib);
            }
            Double na = numeric(a);
            Double nb = numeric(b);
            if (na != null && nb != null) {
                return Double.compare(na, nb);
            }
            if (ia >= 0) {
                return -1;
            }
            if (ib >= 0) {
                return 1;
            }
            return a.compareToIgnoreCase(b);
        }).toList();
    }

    private static Double numeric(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // --- orders ---------------------------------------------------------------------------

    /** @param buyerView true for the buyer (contact revealed after accept); false for the store owner. */
    public List<OrderResponse> orders(List<WholesaleOrder> orders, boolean buyerView) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(WholesaleOrder::getId).toList();
        Map<Long, List<WholesaleOrderItem>> itemsByOrder = new HashMap<>();
        List<WholesaleOrderItem> allItems = orderItemRepository.findByOrderIdInOrderByIdAsc(orderIds);
        for (WholesaleOrderItem item : allItems) {
            itemsByOrder.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
        }
        Map<Long, java.util.UUID> variantUuids = variantRepository
                .findAllById(allItems.stream().map(WholesaleOrderItem::getVariantId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(WholesaleVariant::getId, WholesaleVariant::getUuid));
        Map<Long, java.util.UUID> productUuids = productRepository
                .findAllById(allItems.stream().map(WholesaleOrderItem::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(WholesaleProduct::getId, WholesaleProduct::getUuid));
        Map<Long, WholesaleStore> stores = storeRepository
                .findByIdIn(orders.stream().map(WholesaleOrder::getStoreId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(WholesaleStore::getId, s -> s));
        Map<Long, User> buyers = new HashMap<>();
        if (!buyerView) {
            userRepository.findAllById(orders.stream().map(WholesaleOrder::getBuyerUserId).collect(Collectors.toSet()))
                    .forEach(u -> buyers.put(u.getId(), u));
        }

        List<OrderResponse> result = new ArrayList<>(orders.size());
        for (WholesaleOrder order : orders) {
            WholesaleStore store = stores.get(order.getStoreId());
            List<OrderResponse.Item> items = itemsByOrder.getOrDefault(order.getId(), List.of()).stream()
                    .map(i -> new OrderResponse.Item(
                            i.getUuid(),
                            variantUuids.get(i.getVariantId()),
                            productUuids.get(i.getProductId()),
                            i.getProductTitle(),
                            i.getColorLabel(),
                            i.getSizeLabel(),
                            i.getSku(),
                            i.getUnitPrice(),
                            i.getAcceptedUnitPrice(),
                            i.getQuantity(),
                            i.getLineTotal()))
                    .toList();
            OrderResponse.SupplierContact contact = null;
            if (buyerView && store != null
                    && (order.getStatus() == WholesaleOrder.Status.ACCEPTED
                    || order.getStatus() == WholesaleOrder.Status.FULFILLED)) {
                contact = new OrderResponse.SupplierContact(store.getPhone(), store.getEmail());
            }
            OrderResponse.BuyerInfo buyer = null;
            if (!buyerView) {
                User user = buyers.get(order.getBuyerUserId());
                if (user != null) {
                    buyer = new OrderResponse.BuyerInfo(user.getName(), user.getEmail(), user.getMobile());
                }
            }
            result.add(new OrderResponse(
                    order.getUuid(),
                    order.getOrderNumber(),
                    order.getStatus().name(),
                    order.getBuyerNote(),
                    order.getSupplierNote(),
                    order.getSubtotal(),
                    order.getAcceptedTotal(),
                    order.getCurrency(),
                    order.getPlacedAt(),
                    order.getAcceptedAt(),
                    order.getRejectedAt(),
                    store == null ? null : store.getUuid(),
                    store == null ? null : store.getStoreName(),
                    items,
                    contact,
                    buyer));
        }
        return result;
    }
}
