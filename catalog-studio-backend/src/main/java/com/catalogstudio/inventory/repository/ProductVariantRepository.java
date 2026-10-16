package com.catalogstudio.inventory.repository;

import com.catalogstudio.inventory.entity.ProductVariant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findByUuid(UUID uuid);

    List<ProductVariant> findByProductIdOrderByCreatedAtAsc(Long productId);

    @Query("""
            select v from ProductVariant v
            join fetch v.product p
            where v.uuid = :uuid and p.user.id = :userId
            """)
    Optional<ProductVariant> findByUuidAndUserId(@Param("uuid") UUID uuid, @Param("userId") Long userId);

    boolean existsByProductIdAndSkuIgnoreCase(Long productId, String sku);
}
