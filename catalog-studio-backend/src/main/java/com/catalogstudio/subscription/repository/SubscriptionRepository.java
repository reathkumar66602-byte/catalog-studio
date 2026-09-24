package com.catalogstudio.subscription.repository;

import com.catalogstudio.subscription.entity.Subscription;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"plan", "pendingPlan", "user"})
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.user.id IN :userIds
              AND s.createdAt = (
                  SELECT MAX(s2.createdAt) FROM Subscription s2 WHERE s2.user.id = s.user.id
              )
            """)
    List<Subscription> findLatestForUsers(@Param("userIds") Collection<Long> userIds);
}
