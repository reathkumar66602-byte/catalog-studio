package com.catalogstudio.user.dto;

import java.util.UUID;

public record MeResponse(
        UUID id,
        String name,
        String username,
        String email,
        String mobile,
        String role,
        boolean emailVerified,
        boolean mobileVerified,
        String preferredAiProvider,
        String preferredLocale,
        String businessName,
        String gstNumber,
        String address,
        String plan,
        String theme,
        String planStatus,
        boolean accessEntitled,
        boolean requiresRecharge,
        String trialEndsOn,
        long daysRemaining
) {}
