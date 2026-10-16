package com.catalogstudio.marketplace.repository;

import com.catalogstudio.marketplace.entity.MarketplaceSyncRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceSyncRunRepository extends JpaRepository<MarketplaceSyncRun, Long> {

    List<MarketplaceSyncRun> findTop20ByUserIdAndMarketplaceOrderByStartedAtDesc(Long userId, String marketplace);
}
