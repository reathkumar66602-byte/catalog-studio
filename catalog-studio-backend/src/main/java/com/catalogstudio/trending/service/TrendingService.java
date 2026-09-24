package com.catalogstudio.trending.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.trending.dto.TrendingCategoryResponse;
import com.catalogstudio.trending.dto.TrendingPageResponse;
import com.catalogstudio.trending.dto.TrendingProductResponse;
import com.catalogstudio.trending.entity.TrendingFeedItem;
import com.catalogstudio.trending.entity.UserTrendingProduct;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrendingService {

    private final List<TrendingMarketplaceClient> clientList;
    private final TrendingFeedStore feedStore;
    private final TrendingSnapshotStore snapshotStore;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> meeshoThinUntil = new ConcurrentHashMap<>();
    private Map<String, TrendingMarketplaceClient> clients = Map.of();

    @PostConstruct
    public void indexClients() {
        Map<String, TrendingMarketplaceClient> indexed = new LinkedHashMap<>();
        for (TrendingMarketplaceClient client : clientList) {
            indexed.put(client.marketplace(), client);
        }
        clients = Map.copyOf(indexed);
    }

    public List<TrendingCategoryResponse> categories() {
        return TrendingCatalog.groups().stream()
                .map(group -> new TrendingCategoryResponse(
                        group.category().key(),
                        group.category().label(),
                        group.children().stream()
                                .map(child -> new TrendingCategoryResponse(child.key(), child.label(), List.of()))
                                .toList()))
                .toList();
    }

    public TrendingPageResponse products(Long userId, String marketplace, String categoryKey, Integer requestedPage) {
        String market = TrendingCatalog.requireMarketplace(marketplace);
        TrendingCatalog.Category category = TrendingCatalog.requireCategory(categoryKey);
        if (requestedPage != null && (requestedPage < 0 || requestedPage > TrendingCatalog.MAX_PAGE)) {
            throw ApiException.badRequest("Ask for a page between 1 and " + (TrendingCatalog.MAX_PAGE + 1));
        }
        List<UserTrendingProduct> saved = snapshotStore.saved(userId, market, category.key());
        if (!saved.isEmpty()
                && (requestedPage == null || saved.get(0).getPageIndex() == requestedPage)
                && !refreshSaved(market, saved)) {
            return fromSaved(market, category, saved, true);
        }
        int page = requestedPage == null ? 0 : requestedPage;
        List<TrendingHit> hits = window(market, category, page);
        if (hits.isEmpty()) {
            return new TrendingPageResponse(
                    market,
                    category.key(),
                    category.label(),
                    page,
                    TrendingCatalog.PAGE_SIZE,
                    false,
                    false,
                    null,
                    List.of());
        }
        List<UserTrendingProduct> stored = snapshotStore.replace(userId, market, category.key(), page, hits);
        return fromSaved(market, category, stored, false);
    }

    private List<TrendingHit> window(String marketplace, TrendingCatalog.Category category, int page) {
        TrendingMarketplaceClient client = clients.get(marketplace);
        if (client == null) {
            throw ApiException.badRequest("Choose Flipkart, Meesho, or Amazon");
        }
        Object lock = locks.computeIfAbsent(marketplace + ":" + category.key(), key -> new Object());
        synchronized (lock) {
            String feedKey = marketplace + ":" + category.key();
            TrendingFeedStore.Snapshot snap = feedStore.read(marketplace, category.key());
            if (snap.state() != null && !snap.fresh()) {
                feedStore.reset(marketplace, category.key());
                snap = new TrendingFeedStore.Snapshot(List.of(), null, false);
            }
            if ("MEESHO".equals(marketplace)
                    && meeshoPageUnusable(snap.items(), page)
                    && !meeshoThinHold(feedKey)) {
                feedStore.reset(marketplace, category.key());
                snap = new TrendingFeedStore.Snapshot(List.of(), null, false);
            }
            int needed = (page * TrendingCatalog.PAGE_SIZE) + TrendingCatalog.PAGE_SIZE;
            int guard = 0;
            while (snap.items().size() < needed && guard++ < 8) {
                int before = snap.items().size();
                String cursor = snap.state() == null ? null : snap.state().getNextCursor();
                int nextPage = snap.state() == null || snap.state().getNextPage() < 1 ? 1 : snap.state().getNextPage();
                Instant fetchedAt = snap.state() == null || snap.state().getFetchedAt() == null
                        ? Instant.now()
                        : snap.state().getFetchedAt();
                TrendingBatch batch = client.nextBatch(category, cursor, nextPage);
                List<TrendingHit> freshHits = unseen(snap.items(), batch.products());
                if (freshHits.isEmpty()) {
                    if (batch.cursor() != null || batch.nextPage() != nextPage) {
                        feedStore.append(marketplace, category.key(), List.of(), before, batch.cursor(), batch.nextPage(), fetchedAt);
                    }
                    break;
                }
                snap = feedStore.append(
                        marketplace,
                        category.key(),
                        freshHits,
                        before,
                        batch.cursor(),
                        batch.nextPage(),
                        fetchedAt);
                if (snap.items().size() <= before) {
                    break;
                }
            }
            List<TrendingHit> pageHits = slice(snap.items(), page);
            if ("MEESHO".equals(marketplace)) {
                if (!pageHits.isEmpty() && meeshoHitsUnusable(pageHits)) {
                    meeshoThinUntil.put(feedKey, Instant.now().plus(Duration.ofMinutes(20)));
                } else if (!pageHits.isEmpty()) {
                    meeshoThinUntil.remove(feedKey);
                }
            }
            return pageHits;
        }
    }

    private boolean meeshoThinHold(String feedKey) {
        Instant until = meeshoThinUntil.get(feedKey);
        if (until == null) {
            return false;
        }
        if (until.isAfter(Instant.now())) {
            return true;
        }
        meeshoThinUntil.remove(feedKey);
        return false;
    }

    private static boolean refreshSaved(String marketplace, List<UserTrendingProduct> rows) {
        if (!"MEESHO".equals(marketplace)) {
            return false;
        }
        Set<String> images = new HashSet<>();
        for (UserTrendingProduct row : rows) {
            if (!OpenAiMeeshoLookup.usableCard(row.getTitle(), row.getImageUrl())) {
                return true;
            }
            if (row.getImageUrl() != null && !images.add(row.getImageUrl())) {
                return true;
            }
        }
        return false;
    }

    private static boolean meeshoPageUnusable(List<TrendingFeedItem> items, int page) {
        int start = page * TrendingCatalog.PAGE_SIZE;
        if (items.size() <= start) {
            return false;
        }
        int end = Math.min(items.size(), start + TrendingCatalog.PAGE_SIZE);
        Set<String> images = new HashSet<>();
        for (int i = start; i < end; i++) {
            TrendingFeedItem item = items.get(i);
            if (!OpenAiMeeshoLookup.usableCard(item.getTitle(), item.getImageUrl())) {
                return true;
            }
            if (item.getImageUrl() != null && !images.add(item.getImageUrl())) {
                return true;
            }
        }
        return false;
    }

    private static boolean meeshoHitsUnusable(List<TrendingHit> hits) {
        for (TrendingHit hit : hits) {
            if (!OpenAiMeeshoLookup.usableCard(hit.title(), hit.imageUrl())) {
                return true;
            }
        }
        return false;
    }

    private static List<TrendingHit> unseen(List<TrendingFeedItem> existing, List<TrendingHit> incoming) {
        Set<String> seen = new HashSet<>();
        for (TrendingFeedItem item : existing) {
            seen.add(item.getExternalId());
        }
        List<TrendingHit> fresh = new ArrayList<>();
        for (TrendingHit hit : incoming) {
            if (hit.externalId() != null && seen.add(hit.externalId())) {
                fresh.add(hit);
            }
        }
        return fresh;
    }

    private static List<TrendingHit> slice(List<TrendingFeedItem> items, int page) {
        int start = page * TrendingCatalog.PAGE_SIZE;
        if (start >= items.size()) {
            return List.of();
        }
        int end = Math.min(items.size(), start + TrendingCatalog.PAGE_SIZE);
        List<TrendingHit> hits = new ArrayList<>();
        for (int i = start; i < end; i++) {
            TrendingFeedItem item = items.get(i);
            hits.add(new TrendingHit(
                    item.getExternalId(),
                    item.getTitle(),
                    item.getBrand(),
                    item.getPriceLabel(),
                    item.getMrpLabel(),
                    item.getRating(),
                    item.getReviewCount(),
                    item.getImageUrl(),
                    item.getProductUrl()));
        }
        return hits;
    }

    private static TrendingPageResponse fromSaved(
            String marketplace,
            TrendingCatalog.Category category,
            List<UserTrendingProduct> rows,
            boolean cached
    ) {
        int page = rows.isEmpty() ? 0 : rows.get(0).getPageIndex();
        Instant seenAt = rows.isEmpty() ? null : rows.get(0).getSeenAt();
        List<TrendingProductResponse> products = rows.stream()
                .map(row -> new TrendingProductResponse(
                        row.getExternalId(),
                        row.getTitle(),
                        row.getBrand(),
                        row.getPriceLabel(),
                        row.getMrpLabel(),
                        row.getRating(),
                        row.getReviewCount(),
                        row.getImageUrl(),
                        row.getProductUrl()))
                .toList();
        return new TrendingPageResponse(
                marketplace,
                category.key(),
                category.label(),
                page,
                TrendingCatalog.PAGE_SIZE,
                cached,
                products.size() == TrendingCatalog.PAGE_SIZE,
                seenAt,
                products);
    }
}
