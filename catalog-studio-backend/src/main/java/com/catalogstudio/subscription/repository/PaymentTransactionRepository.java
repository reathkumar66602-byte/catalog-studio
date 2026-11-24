package com.catalogstudio.subscription.repository;

import com.catalogstudio.subscription.entity.PaymentTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    @EntityGraph(attributePaths = {"plan", "user"})
    Page<PaymentTransaction> findByUser_Id(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"plan", "user"})
    Page<PaymentTransaction> findByUser_IdAndTypeNot(
            Long userId, PaymentTransaction.TransactionType type, Pageable pageable);

    List<PaymentTransaction> findTop5ByUser_IdOrderByCreatedAtDesc(Long userId);

    List<PaymentTransaction> findTop5ByUser_IdAndTypeNotOrderByCreatedAtDesc(
            Long userId, PaymentTransaction.TransactionType type);

    long countByUser_Id(Long userId);

    long countByUser_IdAndTypeNot(Long userId, PaymentTransaction.TransactionType type);

    @EntityGraph(attributePaths = {"plan", "user"})
    Optional<PaymentTransaction> findByUuidAndUser_Id(UUID uuid, Long userId);

    @EntityGraph(attributePaths = {"plan", "user"})
    Optional<PaymentTransaction> findByGatewayLinkId(String gatewayLinkId);

    @EntityGraph(attributePaths = {"plan", "user"})
    Optional<PaymentTransaction> findFirstByUser_IdAndPlanNameIgnoreCaseAndTypeAndStatusOrderByCreatedAtDesc(
            Long userId,
            String planName,
            PaymentTransaction.TransactionType type,
            PaymentTransaction.TransactionStatus status
    );

    boolean existsByReferenceAndTypeAndStatus(
            String reference,
            PaymentTransaction.TransactionType type,
            PaymentTransaction.TransactionStatus status
    );

    @EntityGraph(attributePaths = {"plan", "user"})
    Optional<PaymentTransaction> findByUuid(UUID uuid);

    @EntityGraph(attributePaths = {"plan", "user"})
    @Query("""
            SELECT t FROM PaymentTransaction t
            WHERE (:q = '' OR LOWER(t.user.email) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(t.user.name, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(t.reference, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(t.invoiceNumber, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(t.gatewayOrderId, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(t.gatewayPaymentId, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(t.planName) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:provider = '' OR UPPER(t.provider) = UPPER(:provider))
              AND (:status IS NULL OR t.status = :status)
              AND (:type IS NULL OR t.type = :type)
            """)
    Page<PaymentTransaction> searchAdminLedger(
            @Param("q") String q,
            @Param("provider") String provider,
            @Param("status") PaymentTransaction.TransactionStatus status,
            @Param("type") PaymentTransaction.TransactionType type,
            Pageable pageable
    );
}
