package com.catalogstudio.marketplace.repository;

import com.catalogstudio.marketplace.entity.MarketplaceAttributeMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceAttributeMappingRepository extends JpaRepository<MarketplaceAttributeMapping, Long> {
    List<MarketplaceAttributeMapping> findByMarketplace(String marketplace);
    List<MarketplaceAttributeMapping> findByMarketplaceAndInternalAttribute(String marketplace, String internalAttribute);
}
