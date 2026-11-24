package com.catalogstudio.wholesale.repository;

import com.catalogstudio.wholesale.entity.WholesaleCouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WholesaleCouponRedemptionRepository extends JpaRepository<WholesaleCouponRedemption, Long> {

    boolean existsByOrderId(Long orderId);
}
