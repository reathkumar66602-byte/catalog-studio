package com.catalogstudio.trending.repository;

import com.catalogstudio.trending.entity.TrendingFeedState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrendingFeedStateRepository extends JpaRepository<TrendingFeedState, Long> {

    Optional<TrendingFeedState> findByMarketplaceAndCategoryKey(String marketplace, String categoryKey);

    void deleteByMarketplaceAndCategoryKey(String marketplace, String categoryKey);
}
