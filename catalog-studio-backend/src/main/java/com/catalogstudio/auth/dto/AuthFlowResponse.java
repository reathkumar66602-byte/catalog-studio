package com.catalogstudio.auth.dto;

import java.util.UUID;

public record AuthFlowResponse(
        boolean otpRequired,
        UUID challengeId,
        String maskedEmail,
        int otpExpiresInSeconds,
        int resendAfterSeconds,
        String purpose,
        AuthResponse session
) {
    public static AuthFlowResponse otp(OtpChallengeView challenge) {
        return new AuthFlowResponse(
                true,
                challenge.challengeId(),
                challenge.maskedEmail(),
                challenge.otpExpiresInSeconds(),
                challenge.resendAfterSeconds(),
                challenge.purpose(),
                null
        );
    }

    public static AuthFlowResponse session(AuthResponse session) {
        return new AuthFlowResponse(false, null, null, 0, 0, null, session);
    }

    public record OtpChallengeView(
            UUID challengeId,
            String maskedEmail,
            int otpExpiresInSeconds,
            int resendAfterSeconds,
            String purpose
    ) {}
}
