package com.catalogstudio.business.repository;

import com.catalogstudio.business.entity.Business;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, Long> {
    Optional<Business> findByUserId(Long userId);
    Optional<Business> findByUuid(UUID uuid);
}
