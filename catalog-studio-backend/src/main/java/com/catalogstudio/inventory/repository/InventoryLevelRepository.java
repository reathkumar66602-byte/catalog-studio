package com.catalogstudio.inventory.repository;

import com.catalogstudio.inventory.entity.InventoryLevel;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, Long> {

    Optional<InventoryLevel> findByVariantId(Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from InventoryLevel l where l.id = :id")
    Optional<InventoryLevel> lockById(@Param("id") Long id);

    @Query("""
            select l from InventoryLevel l
            join fetch l.variant v
            join fetch v.product p
            where p.user.id = :userId
            order by v.sku asc
            """)
    List<InventoryLevel> findAllForUser(@Param("userId") Long userId);

    @Query("""
            select l from InventoryLevel l
            join fetch l.variant v
            join fetch v.product p
            where v.uuid = :variantId and p.user.id = :userId
            """)
    Optional<InventoryLevel> findByVariantUuidAndUserId(
            @Param("variantId") UUID variantId, @Param("userId") Long userId);
}
