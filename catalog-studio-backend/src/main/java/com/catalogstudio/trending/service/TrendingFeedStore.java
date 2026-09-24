package com.catalogstudio.trending.service;

import com.catalogstudio.trending.entity.TrendingFeedItem;
import com.catalogstudio.trending.entity.TrendingFeedState;
import com.catalogstudio.trending.repository.TrendingFeedItemRepository;
import com.catalogstudio.trending.repository.TrendingFeedStateRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrendingFeedStore {

    private final TrendingFeedItemRepository items;
    private final TrendingFeedStateRepository states;

    @Transactional(readOnly = true)
    public Snapshot read(String marketplace, String categoryKey) {
        TrendingFeedState state = states.findByMarketplaceAndCategoryKey(marketplace, categoryKey).orElse(null);
        if (state == null) {
            return new Snapshot(List.of(), null, false);
        }
        boolean fresh = state.getFetchedAt() != null
                && state.getFetchedAt().isAfter(Instant.now().minus(TrendingCatalog.FEED_TTL));
        List<TrendingFeedItem> rows = items.findByMarketplaceAndCategoryKeyOrderByRankIndexAsc(marketplace, categoryKey);
        return new Snapshot(rows, state, fresh);
    }

    @Transactional
    public void reset(String marketplace, String categoryKey) {
        items.deleteByMarketplaceAndCategoryKey(marketplace, categoryKey);
        states.deleteByMarketplaceAndCategoryKey(marketplace, categoryKey);
    }

    @Transactional
    public Snapshot append(
            String marketplace,
            String categoryKey,
            List<TrendingHit> hits,
            int fromRank,
            String cursor,
            int nextPage,
            Instant fetchedAt
    ) {
        Instant stamp = fetchedAt == null ? Instant.now() : fetchedAt;
        int rank = fromRank;
        for (TrendingHit hit : hits) {
            items.save(TrendingFeedItem.builder()
                    .marketplace(marketplace)
                    .categoryKey(categoryKey)
                    .rankIndex(rank++)
                    .externalId(hit.externalId())
                    .title(blankTitle(hit.title()))
                    .brand(hit.brand())
                    .priceLabel(hit.priceLabel())
                    .mrpLabel(hit.mrpLabel())
                    .rating(hit.rating())
                    .reviewCount(hit.reviewCount())
                    .imageUrl(hit.imageUrl())
                    .productUrl(hit.productUrl() == null ? "https://catalog.studio" : hit.productUrl())
                    .fetchedAt(stamp)
                    .build());
        }
        TrendingFeedState state = states.findByMarketplaceAndCategoryKey(marketplace, categoryKey)
                .orElseGet(() -> TrendingFeedState.builder()
                        .marketplace(marketplace)
                        .categoryKey(categoryKey)
                        .fetchedAt(stamp)
                        .build());
        if (state.getFetchedAt() == null) {
            state.setFetchedAt(stamp);
        }
        state.setItemCount(rank);
        state.setNextCursor(cursor);
        state.setNextPage(Math.max(nextPage, 1));
        states.save(state);
        List<TrendingFeedItem> rows = items.findByMarketplaceAndCategoryKeyOrderByRankIndexAsc(marketplace, categoryKey);
        return new Snapshot(rows, state, true);
    }

    private static String blankTitle(String title) {
        return title == null || title.isBlank() ? "Product" : title;
    }

    public record Snapshot(List<TrendingFeedItem> items, TrendingFeedState state, boolean fresh) {}
}
