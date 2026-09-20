package com.catalogstudio.analysis.repository;

import com.catalogstudio.analysis.entity.ProductAnalysis;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductAnalysisRepository extends JpaRepository<ProductAnalysis, Long> {

    @EntityGraph(attributePaths = {"product", "product.images"})
    Optional<ProductAnalysis> findByUuidAndUserId(UUID uuid, Long userId);

    @EntityGraph(attributePaths = {"product", "product.images"})
    @Query("select a from ProductAnalysis a where a.user.id = :userId")
    Page<ProductAnalysis> findByUserId(@Param("userId") Long userId, Pageable pageable);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    long countByCreatedAtAfter(Instant after);
}
