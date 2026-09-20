package com.catalogstudio.referral.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.referral.dto.ReferralLookupResponse;
import com.catalogstudio.referral.service.ReferralService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
@Tag(name = "Referrals")
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping("/{code}")
    @Operation(summary = "Validate a referral or coupon code")
    public ApiResponse<ReferralLookupResponse> lookup(@PathVariable String code) {
        ReferralLookupResponse data = referralService.lookup(code);
        return ApiResponse.ok(data.valid() ? "Referral code applied" : "Referral code is invalid", data);
    }
}
