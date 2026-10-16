package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.WholesaleStore;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WholesaleStoreRepository extends JpaRepository<WholesaleStore, Long> {

    Optional<WholesaleStore> findByOwnerUserId(Long ownerUserId);

    Optional<WholesaleStore> findByUuid(UUID uuid);

    Optional<WholesaleStore> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByGstin(String gstin);

    List<WholesaleStore> findByIdIn(Collection<Long> ids);

    Page<WholesaleStore> findByStatus(WholesaleStore.Status status, Pageable pageable);

    @Query("""
            SELECT s FROM WholesaleStore s
            WHERE s.status = com.catalogstudio.wholesale.entity.WholesaleStore.Status.ACTIVE
              AND s.id IN (SELECT sc.storeId FROM WholesaleStoreCategory sc WHERE sc.categoryId = :categoryId)
            """)
    Page<WholesaleStore> findActiveByCategory(@Param("categoryId") Long categoryId, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE wholesale_stores SET product_count = GREATEST(product_count + :delta, 0), updated_at = NOW() WHERE id = :id",
            nativeQuery = true)
    int adjustProductCount(@Param("id") Long id, @Param("delta") int delta);
}