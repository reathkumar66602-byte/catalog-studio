package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.WholesaleOrder;
import jakarta.persistence.LockModeType;
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
}