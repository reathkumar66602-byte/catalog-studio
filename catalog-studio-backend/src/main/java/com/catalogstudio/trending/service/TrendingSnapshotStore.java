package com.catalogstudio.trending.service;

import com.catalogstudio.trending.entity.UserTrendingProduct;
import com.catalogstudio.trending.repository.UserTrendingProductRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrendingSnapshotStore {

    private final UserTrendingProductRepository products;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<UserTrendingProduct> saved(Long userId, String marketplace, String categoryKey) {
        return products.findByUser_IdAndMarketplaceAndCategoryKeyOrderBySlotAsc(userId, marketplace, categoryKey);
    }

    @Transactional
    public List<UserTrendingProduct> replace(Long userId, String marketplace, String categoryKey, int page, List<TrendingHit> hits) {
        products.deleteByUser_IdAndMarketplaceAndCategoryKey(userId, marketplace, categoryKey);
        products.flush();
        if (hits.isEmpty()) {
            return List.of();
        }
        User user = users.getReferenceById(userId);
        Instant now = Instant.now();
        List<UserTrendingProduct> rows = new ArrayList<>();
        int slot = 0;
        for (TrendingHit hit : hits) {
            if (slot >= TrendingCatalog.PAGE_SIZE) {
                break;
            }
            rows.add(UserTrendingProduct.builder()
                    .user(user)
                    .marketplace(marketplace)
                    .categoryKey(categoryKey)
                    .pageIndex(page)
                    .slot(slot)
                    .externalId(hit.externalId())
                    .title(hit.title() == null || hit.title().isBlank() ? "Product" : hit.title())
                    .brand(hit.brand())
                    .priceLabel(hit.priceLabel())
                    .mrpLabel(hit.mrpLabel())
                    .rating(hit.rating())
                    .reviewCount(hit.reviewCount())
                    .imageUrl(hit.imageUrl())
                    .productUrl(hit.productUrl())
                    .seenAt(now)
                    .build());
            slot++;
        }
        return products.saveAll(rows);
    }
}
