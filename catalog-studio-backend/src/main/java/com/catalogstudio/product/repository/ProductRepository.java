package com.catalogstudio.product.repository;

import com.catalogstudio.product.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByUuidAndUserId(UUID uuid, Long userId);

    Page<Product> findByUserId(Long userId, Pageable pageable);

    Optional<Product> findByUuid(UUID uuid);

    long countByUserId(Long userId);
}
