package com.catalogstudio.product.repository;

import com.catalogstudio.product.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByUuidAndUserId(UUID uuid, Long userId);

    @Query("select p from Product p where p.user.id = :userId and lower(p.sku) = lower(:sku)")
    Optional<Product> findByUserIdAndSkuIgnoreCase(@Param("userId") Long userId, @Param("sku") String sku);

    Page<Product> findByUserId(Long userId, Pageable pageable);

    Optional<Product> findByUuid(UUID uuid);

    long countByUserId(Long userId);
}
