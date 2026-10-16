package com.catalogstudio.marketplace.repository;

import com.catalogstudio.marketplace.entity.OmsAutoAction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OmsAutoActionRepository extends JpaRepository<OmsAutoAction, Long> {
    List<OmsAutoAction> findByUserIdAndEnabledTrueOrderByCreatedAtAsc(Long userId);

    List<OmsAutoAction> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<OmsAutoAction> findByUuidAndUserId(UUID uuid, Long userId);
}
