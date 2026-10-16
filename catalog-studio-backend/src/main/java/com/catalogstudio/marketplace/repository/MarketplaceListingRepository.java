package com.catalogstudio.marketplace.repository;

import com.catalogstudio.marketplace.entity.MarketplaceListing;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, Long> {

    Optional<MarketplaceListing> findByVariantIdAndConnectionId(Long variantId, Long connectionId);

    @Query("""
            select l from MarketplaceListing l
            join fetch l.variant v
            join fetch v.product p
            join fetch l.connection c
            where p.user.id = :userId
            order by l.updatedAt desc
            """)
    List<MarketplaceListing> findAllForUser(@Param("userId") Long userId);

    Optional<MarketplaceListing> findByUuidAndVariant_Product_User_Id(UUID uuid, Long userId);
}
