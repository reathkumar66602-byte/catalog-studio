package com.catalogstudio.subscription.repository;

import com.catalogstudio.subscription.entity.Subscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
