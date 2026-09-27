package com.catalogstudio.subscription.repository;

import com.catalogstudio.subscription.entity.BillingPromoCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPromoCodeRepository extends JpaRepository<BillingPromoCode, Long> {

    Optional<BillingPromoCode> findByUuid(UUID uuid);

    Optional<BillingPromoCode> findByCodeIgnoreCase(String code);

    List<BillingPromoCode> findByStatusOrderByCodeAsc(String status);

    List<BillingPromoCode> findAllByOrderByCreatedAtDesc();

    boolean existsByCodeIgnoreCase(String code);
}
