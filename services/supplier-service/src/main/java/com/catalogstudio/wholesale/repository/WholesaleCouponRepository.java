package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.WholesaleCoupon;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WholesaleCouponRepository extends JpaRepository<WholesaleCoupon, Long> {

    List<WholesaleCoupon> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    Optional<WholesaleCoupon> findByUuid(UUID uuid);

    Optional<WholesaleCoupon> findByStoreIdAndUuid(Long storeId, UUID uuid);

    Optional<WholesaleCoupon> findByStoreIdAndCodeIgnoreCase(Long storeId, String code);

    boolean existsByStoreIdAndCodeIgnoreCase(Long storeId, String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM WholesaleCoupon c WHERE c.storeId = :storeId AND UPPER(c.code) = UPPER(:code)")
    Optional<WholesaleCoupon> lockByStoreIdAndCode(
            @Param("storeId") Long storeId, @Param("code") String code);
}
