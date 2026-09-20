package com.catalogstudio.referral.repository;

import com.catalogstudio.referral.entity.Referral;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralRepository extends JpaRepository<Referral, Long> {
    boolean existsByReferredId(Long referredId);
}
