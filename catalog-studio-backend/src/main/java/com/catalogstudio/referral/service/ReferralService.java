package com.catalogstudio.referral.service;

import com.catalogstudio.referral.dto.ReferralLookupResponse;
import com.catalogstudio.referral.entity.ReferralCode;
import com.catalogstudio.referral.repository.ReferralCodeRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private final ReferralCodeRepository referralCodeRepository;

    public ReferralLookupResponse lookup(String code) {
        return referralCodeRepository.findByCodeIgnoreCase(code)
                .filter(ReferralCode::isActive)
                .map(ref -> new ReferralLookupResponse(
                        ref.getCode(),
                        true,
                        ref.getDiscountType(),
                        ref.getDiscountValue(),
                        ref.getTrialDays(),
                        ref.getDescription()
                ))
                .orElse(new ReferralLookupResponse(code, false, null, BigDecimal.ZERO, 0, "Invalid referral code"));
    }
}
