package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.WholesaleVariant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WholesaleVariantRepository extends JpaRepository<WholesaleVariant, Long> {

    List<WholesaleVariant> findByProductIdOrderByColorLabelAscSizeLabelAsc(Long productId);

    List<WholesaleVariant> findByProductIdAndStatus(Long productId, WholesaleVariant.Status status);

    List<WholesaleVariant> findByUuidIn(Collection<UUID> uuids);

    Optional<WholesaleVariant> findByUuid(UUID uuid);

    boolean existsByProductIdAndSkuIgnoreCase(Long productId, String sku);

    /** Distinct colours (label, hex) per product for product cards: [productId, colorLabel, colorHex]. */
    @Query("""
            SELECT DISTINCT v.productId, v.colorLabel, v.colorHex FROM WholesaleVariant v
            WHERE v.productId IN :productIds AND v.status = com.catalogstudio.wholesale.entity.WholesaleVariant.Status.ACTIVE
            """)
    List<Object[]> findColorsByProductIds(@Param("productIds") Collection<Long> productIds);
}