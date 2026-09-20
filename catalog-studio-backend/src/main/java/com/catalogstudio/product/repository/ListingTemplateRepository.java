package com.catalogstudio.product.repository;

import com.catalogstudio.product.entity.ListingTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingTemplateRepository extends JpaRepository<ListingTemplate, Long> {
    Page<ListingTemplate> findByUserId(Long userId, Pageable pageable);

    List<ListingTemplate> findByUserId(Long userId);
    Optional<ListingTemplate> findByUuidAndUserId(UUID uuid, Long userId);
    long countByUserId(Long userId);
}
