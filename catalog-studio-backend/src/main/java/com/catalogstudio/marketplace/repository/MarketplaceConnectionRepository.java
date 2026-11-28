package com.catalogstudio.marketplace.repository;

import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketplaceConnectionRepository extends JpaRepository<MarketplaceConnection, Long> {
    List<MarketplaceConnection> findByUserId(Long userId);
    Optional<MarketplaceConnection> findByUserIdAndMarketplace(Long userId, String marketplace);
    long countByUserIdAndStatus(Long userId, MarketplaceConnection.ConnectionStatus status);

    @Query("""
            select c from MarketplaceConnection c
            join fetch c.user
            where upper(c.marketplace) = 'MEESHO'
              and c.status = com.catalogstudio.marketplace.entity.MarketplaceConnection.ConnectionStatus.CONNECTED
              and upper(c.connectionType) = 'PANEL_LOGIN'
            """)
    List<MarketplaceConnection> findConnectedMeeshoPanel();
}
