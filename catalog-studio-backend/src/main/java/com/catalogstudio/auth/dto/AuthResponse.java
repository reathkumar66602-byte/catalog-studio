package com.catalogstudio.auth.dto;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserSummary user
) {
    public record UserSummary(
            UUID id,
            String name,
            String email,
            String role,
            boolean emailVerified,
            String businessName,
            String plan,
            String planStatus,
            boolean accessEntitled,
            boolean requiresRecharge,
            String trialEndsOn,
            long daysRemaining,
            String preferredLocale,
            List<String> enabledFeatures
    ) {}
}
