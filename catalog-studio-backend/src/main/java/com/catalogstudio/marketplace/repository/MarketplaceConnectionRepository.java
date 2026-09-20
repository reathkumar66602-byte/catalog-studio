package com.catalogstudio.marketplace.repository;

import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceConnectionRepository extends JpaRepository<MarketplaceConnection, Long> {
    List<MarketplaceConnection> findByUserId(Long userId);
    Optional<MarketplaceConnection> findByUserIdAndMarketplace(Long userId, String marketplace);
    long countByUserIdAndStatus(Long userId, MarketplaceConnection.ConnectionStatus status);
}
