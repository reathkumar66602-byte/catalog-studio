package com.catalogstudio.subscription.repository;

import com.catalogstudio.subscription.entity.PaymentTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    @EntityGraph(attributePaths = {"plan", "user"})
    Page<PaymentTransaction> findByUser_Id(Long userId, Pageable pageable);

    List<PaymentTransaction> findTop5ByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByUser_Id(Long userId);

    @EntityGraph(attributePaths = {"plan", "user"})
    Optional<PaymentTransaction> findByUuidAndUser_Id(UUID uuid, Long userId);
}
