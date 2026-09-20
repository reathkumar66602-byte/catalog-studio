package com.catalogstudio.subscription.repository;

import com.catalogstudio.subscription.entity.SubscriptionPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findByNameIgnoreCase(String name);
    Optional<SubscriptionPlan> findByUuid(UUID uuid);
    List<SubscriptionPlan> findByStatus(String status);
    List<SubscriptionPlan> findByStatusOrderByPriceAsc(String status);
}
