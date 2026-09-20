package com.catalogstudio.product.repository;

import com.catalogstudio.product.entity.AutofillProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutofillProfileRepository extends JpaRepository<AutofillProfile, Long> {
    Page<AutofillProfile> findByUserId(Long userId, Pageable pageable);
    List<AutofillProfile> findByUserId(Long userId);
    Optional<AutofillProfile> findByUuidAndUserId(UUID uuid, Long userId);
    long countByUserId(Long userId);
}
