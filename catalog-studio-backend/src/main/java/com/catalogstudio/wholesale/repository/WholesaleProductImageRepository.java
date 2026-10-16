package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.WholesaleProductImage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WholesaleProductImageRepository extends JpaRepository<WholesaleProductImage, Long> {

    List<WholesaleProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    List<WholesaleProductImage> findByProductIdInOrderBySortOrderAscIdAsc(Collection<Long> productIds);

    long countByProductId(Long productId);

    Optional<WholesaleProductImage> findByUuidAndProductId(UUID uuid, Long productId);
}