package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.WholesaleOrder;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WholesaleOrderRepository extends JpaRepository<WholesaleOrder, Long> {

    Optional<WholesaleOrder> findByUuid(UUID uuid);

    /** Row lock so accept / reject / cancel cannot race on the same order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM WholesaleOrder o WHERE o.uuid = :uuid")
    Optional<WholesaleOrder> lockByUuid(@Param("uuid") UUID uuid);

    Page<WholesaleOrder> findByBuyerUserId(Long buyerUserId, Pageable pageable);

    Page<WholesaleOrder> findByStoreId(Long storeId, Pageable pageable);

    Page<WholesaleOrder> findByStoreIdAndStatus(Long storeId, WholesaleOrder.Status status, Pageable pageable);

    boolean existsByOrderNumber(String orderNumber);

    @Query("""
            SELECT o FROM WholesaleOrder o
            WHERE COALESCE(o.paidAt, o.acceptedAt, o.placedAt) < :cutoff
            ORDER BY COALESCE(o.paidAt, o.acceptedAt, o.placedAt) ASC, o.id ASC
            """)
    List<WholesaleOrder> findOlderThan(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Query("""
            SELECT o FROM WholesaleOrder o
            WHERE o.buyerUserId = :buyerUserId
              AND o.status IN :statuses
              AND COALESCE(o.paidAt, o.acceptedAt, o.placedAt) >= :from
              AND COALESCE(o.paidAt, o.acceptedAt, o.placedAt) < :to
            """)
    Page<WholesaleOrder> findBuyerBills(
            @Param("buyerUserId") Long buyerUserId,
            @Param("statuses") Collection<WholesaleOrder.Status> statuses,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            SELECT o FROM WholesaleOrder o
            WHERE o.buyerUserId = :buyerUserId
              AND o.status IN :statuses
              AND COALESCE(o.paidAt, o.acceptedAt, o.placedAt) >= :from
              AND COALESCE(o.paidAt, o.acceptedAt, o.placedAt) < :to
            ORDER BY COALESCE(o.paidAt, o.acceptedAt, o.placedAt) DESC, o.id DESC
            """)
    List<WholesaleOrder> listBuyerBills(
            @Param("buyerUserId") Long buyerUserId,
            @Param("statuses") Collection<WholesaleOrder.Status> statuses,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT o FROM WholesaleOrder o
            WHERE o.storeId = :storeId
              AND o.status IN :statuses
              AND COALESCE(o.paidAt, o.acceptedAt, o.placedAt) >= :from
              AND COALESCE(o.paidAt, o.acceptedAt, o.placedAt) < :to
            """)
    Page<WholesaleOrder> findStoreBills(
            @Param("storeId") Long storeId,
            @Param("statuses") Collection<WholesaleOrder.Status> statuses,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            SELECT o FROM WholesaleOrder o
            WHERE o.storeId = :storeId
              AND o.status IN :statuses
              AND COALESCE(o.paidAt, o.acceptedAt, o.placedAt) >= :from
              AND COALESCE(o.paidAt, o.acceptedAt, o.placedAt) < :to
            ORDER BY COALESCE(o.paidAt, o.acceptedAt, o.placedAt) DESC, o.id DESC
            """)
    List<WholesaleOrder> listStoreBills(
            @Param("storeId") Long storeId,
            @Param("statuses") Collection<WholesaleOrder.Status> statuses,
            @Param("from") Instant from,
            @Param("to") Instant to);
}