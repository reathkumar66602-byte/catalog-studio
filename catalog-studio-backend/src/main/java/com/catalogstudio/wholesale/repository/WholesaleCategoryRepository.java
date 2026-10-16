package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.WholesaleCategory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WholesaleCategoryRepository extends JpaRepository<WholesaleCategory, Long> {

    List<WholesaleCategory> findByStatusOrderBySortOrderAscNameAsc(String status);

    Optional<WholesaleCategory> findBySlugIgnoreCase(String slug);

    Optional<WholesaleCategory> findByUuid(UUID uuid);

    List<WholesaleCategory> findByUuidIn(Collection<UUID> uuids);

    /** Atomic denormalised counter update (never below zero). */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE wholesale_categories SET product_count = GREATEST(product_count + :delta, 0) WHERE id = :id",
            nativeQuery = true)
    int adjustProductCount(@Param("id") Long id, @Param("delta") int delta);
}