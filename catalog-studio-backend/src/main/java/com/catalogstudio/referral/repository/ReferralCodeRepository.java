package com.catalogstudio.referral.repository;

import com.catalogstudio.referral.entity.ReferralCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {
    Optional<ReferralCode> findByCodeIgnoreCase(String code);
}
