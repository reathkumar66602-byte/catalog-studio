package com.catalogstudio.trending.repository;

import com.catalogstudio.trending.entity.TrendingFeedItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrendingFeedItemRepository extends JpaRepository<TrendingFeedItem, Long> {

    List<TrendingFeedItem> findByMarketplaceAndCategoryKeyOrderByRankIndexAsc(String marketplace, String categoryKey);

    void deleteByMarketplaceAndCategoryKey(String marketplace, String categoryKey);
}
