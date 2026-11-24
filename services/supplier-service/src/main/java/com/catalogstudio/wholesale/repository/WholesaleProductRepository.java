package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.WholesaleProduct;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WholesaleProductRepository
        extends JpaRepository<WholesaleProduct, Long>, JpaSpecificationExecutor<WholesaleProduct> {

    Optional<WholesaleProduct> findByUuid(UUID uuid);

    Optional<WholesaleProduct> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByStoreIdAndProductCodeIgnoreCase(Long storeId, String code);

    Page<WholesaleProduct> findByStoreId(Long storeId, Pageable pageable);

    Page<WholesaleProduct> findByStoreIdAndStatus(Long storeId, WholesaleProduct.Status status, Pageable pageable);

    /**
     * Recomputes min/max price and sellable stock from ACTIVE variants in a single statement.
     * Sellable stock = SUM(on_hand - reserved).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE wholesale_products p SET
                min_price = (SELECT MIN(v.price) FROM wholesale_variants v
                             WHERE v.product_id = p.id AND v.status = 'ACTIVE'),
                max_price = (SELECT MAX(v.price) FROM wholesale_variants v
                             WHERE v.product_id = p.id AND v.status = 'ACTIVE'),
                total_stock = COALESCE((SELECT SUM(GREATEST(v.on_hand - v.reserved, 0)) FROM wholesale_variants v
                                        WHERE v.product_id = p.id AND v.status = 'ACTIVE'), 0),
                updated_at = NOW()
            WHERE p.id = :id
            """, nativeQuery = true)
    int refreshAggregates(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE wholesale_products SET booked_count = GREATEST(booked_count + :delta, 0), updated_at = NOW() WHERE id = :id",
            nativeQuery = true)
    int adjustBookedCount(@Param("id") Long id, @Param("delta") int delta);
}