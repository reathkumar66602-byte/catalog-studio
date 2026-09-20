package com.catalogstudio.auth.service;

import com.catalogstudio.audit.service.AuditService;
import com.catalogstudio.auth.dto.AuthFlowResponse;
import com.catalogstudio.auth.dto.AuthResponse;
import com.catalogstudio.auth.dto.ForgotPasswordRequest;
import com.catalogstudio.auth.dto.LoginRequest;
import com.catalogstudio.auth.dto.OtpResendRequest;
import com.catalogstudio.auth.dto.OtpVerifyRequest;
import com.catalogstudio.auth.dto.RegisterRequest;
import com.catalogstudio.auth.dto.ResetPasswordRequest;
import com.catalogstudio.auth.entity.EmailVerificationToken;
import com.catalogstudio.auth.entity.PasswordResetToken;
import com.catalogstudio.auth.entity.UserSession;
import com.catalogstudio.auth.repository.EmailVerificationTokenRepository;
import com.catalogstudio.auth.repository.PasswordResetTokenRepository;
import com.catalogstudio.auth.repository.UserSessionRepository;
import com.catalogstudio.business.entity.Business;
import com.catalogstudio.business.repository.BusinessRepository;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.common.util.TokenHasher;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.referral.entity.Referral;
import com.catalogstudio.referral.entity.ReferralCode;
import com.catalogstudio.referral.repository.ReferralCodeRepository;
import com.catalogstudio.referral.repository.ReferralRepository;
import com.catalogstudio.security.AuthUser;
import com.catalogstudio.security.JwtService;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.otp.entity.OtpChallenge;
import com.catalogstudio.otp.service.OtpService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final UserSessionRepository sessionRepository;
    private final EmailVerificationTokenRepository emailTokenRepository;
    private final PasswordResetTokenRepository passwordTokenRepository;
    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralRepository referralRepository;
    private final SubscriptionAccessService subscriptionAccessService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CatalogStudioProperties properties;
    private final AuditService auditService;
    private final OtpService otpService;

    @Transactional
    public AuthFlowResponse register(RegisterRequest request, String ip, String userAgent) {
        if (!request.password().equals(request.confirmPassword())) {
            throw ApiException.badRequest("Password and confirm password do not match");
        }
        if (StringUtils.hasText(request.confirmEmail())
                && !request.email().trim().equalsIgnoreCase(request.confirmEmail().trim())) {
            throw ApiException.badRequest("Email and confirm email do not match");
        }
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null && existing.getStatus() != User.UserStatus.PENDING) {
            throw ApiException.conflict("An account with this email already exists");
        }
        boolean usernameTaken = userRepository.findByUsernameIgnoreCase(username)
                .filter(found -> existing == null || !found.getId().equals(existing.getId()))
                .isPresent();
        if (usernameTaken) {
            throw ApiException.conflict("This username is already taken");
        }
        String displayName = StringUtils.hasText(request.fullName()) ? request.fullName().trim() : username;
        String businessName = StringUtils.hasText(request.businessName()) ? request.businessName().trim() : username;
        String mobile = StringUtils.hasText(request.mobile()) ? request.mobile().trim() : null;

        User user;
        if (existing != null) {
            user = existing;
            user.setUsername(username);
            user.setName(displayName);
            user.setMobile(mobile);
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        } else {
            user = userRepository.save(User.builder()
                    .name(displayName)
                    .username(username)
                    .email(email)
                    .mobile(mobile)
                    .passwordHash(passwordEncoder.encode(request.password()))
                    .role(User.Role.SELLER)
                    .status(properties.otp().enabled() ? User.UserStatus.PENDING : User.UserStatus.ACTIVE)
                    .emailVerified(false)
                    .build());
            businessRepository.save(Business.builder()
                    .user(user)
                    .businessName(businessName)
                    .build());
            assignPlan(user, request.referralCode());
        }

        String verifyToken = TokenHasher.randomToken(32);
        emailTokenRepository.save(EmailVerificationToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256(verifyToken))
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build());
        log.info("Email verification token created for {}", user.getEmail());
        auditService.log(user, "USER_REGISTERED", "USER", user.getUuid().toString(), ip, Map.of("email", user.getEmail()));
        if (properties.otp().enabled()) {
            return AuthFlowResponse.otp(toOtpView(otpService.issue(
                    user, OtpChallenge.Purpose.REGISTER, false, "Registration", ip, userAgent)));
        }
        user.setStatus(User.UserStatus.ACTIVE);
        return AuthFlowResponse.session(issueTokens(user, false, "Registration", ip, userAgent));
    }

    @Transactional
    public AuthFlowResponse login(LoginRequest request, String ip, String userAgent) {
        String identifier = request.identifier();
        if (!StringUtils.hasText(identifier)) {
            throw ApiException.badRequest("Enter your email or username");
        }
        User user = findByLogin(identifier);
        if (user.getStatus() == User.UserStatus.DISABLED) {
            throw ApiException.forbidden("This account has been disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.log(user, "LOGIN_FAILED", "USER", user.getUuid().toString(), ip, Map.of());
            throw ApiException.unauthorized("Invalid email or password");
        }
        auditService.log(user, "LOGIN_SUCCESS", "USER", user.getUuid().toString(), ip,
                Map.of("rememberDevice", request.rememberDevice()));
        if (user.getStatus() == User.UserStatus.PENDING) {
            user.setStatus(User.UserStatus.ACTIVE);
        }
        return AuthFlowResponse.session(issueTokens(user, request.rememberDevice(), request.deviceName(), ip, userAgent));
    }

    @Transactional
    public AuthFlowResponse verifyOtp(OtpVerifyRequest request, String ip, String userAgent) {
        OtpChallenge challenge = otpService.consume(request.challengeId(), request.otp());
        User user = challenge.getUser();
        if (user.getStatus() == User.UserStatus.DISABLED) {
            throw ApiException.forbidden("This account has been disabled");
        }
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(true);
        auditService.log(user, "OTP_VERIFIED", "USER", user.getUuid().toString(), ip,
                Map.of("purpose", challenge.getPurpose().name()));
        String device = StringUtils.hasText(challenge.getDeviceName()) ? challenge.getDeviceName() : userAgent;
        return AuthFlowResponse.session(issueTokens(
                user, challenge.isRememberDevice(), device, challenge.getIpAddress() == null ? ip : challenge.getIpAddress(),
                challenge.getUserAgent() == null ? userAgent : challenge.getUserAgent()));
    }

    @Transactional
    public AuthFlowResponse.OtpChallengeView resendOtp(OtpResendRequest request) {
        return toOtpView(otpService.resendIssued(request.challengeId()));
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        UserSession session = sessionRepository.findByRefreshTokenHash(TokenHasher.sha256(refreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (!session.isActive()) {
            throw ApiException.unauthorized("Refresh token expired or revoked");
        }
        session.setLastUsedAt(Instant.now());
        return tokensFor(session.getUser(), refreshToken, session.getExpiresAt());
    }

    @Transactional
    public void logout(String refreshToken) {
        sessionRepository.findByRefreshTokenHash(TokenHasher.sha256(refreshToken)).ifPresent(session -> {
            session.setRevoked(true);
            auditService.log(session.getUser(), "LOGOUT", "SESSION", session.getUuid().toString(), session.getIpAddress(), Map.of());
        });
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken stored = emailTokenRepository.findByTokenHash(TokenHasher.sha256(token))
                .orElseThrow(() -> ApiException.badRequest("Invalid verification token"));
        if (stored.getUsedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Verification token expired");
        }
        stored.setUsedAt(Instant.now());
        stored.getUser().setEmailVerified(true);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(user -> {
            String token = TokenHasher.randomToken(32);
            passwordTokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(TokenHasher.sha256(token))
                    .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                    .build());
            log.info("Password reset token for {} is {}", user.getEmail(), token);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken stored = passwordTokenRepository.findByTokenHash(TokenHasher.sha256(request.token()))
                .orElseThrow(() -> ApiException.badRequest("Invalid reset token"));
        if (stored.getUsedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Reset token expired");
        }
        stored.setUsedAt(Instant.now());
        stored.getUser().setPasswordHash(passwordEncoder.encode(request.password()));
        sessionRepository.revokeAllForUser(stored.getUser().getId());
    }

    @Transactional
    public void logoutOtherDevices(Long userId, String currentRefreshToken) {
        String currentHash = currentRefreshToken == null ? "" : TokenHasher.sha256(currentRefreshToken);
        sessionRepository.findByUserIdAndRevokedFalse(userId).forEach(session -> {
            if (!session.getRefreshTokenHash().equals(currentHash)) {
                session.setRevoked(true);
            }
        });
    }

    private void assignPlan(User user, String referralCode) {
        Integer referralTrialDays = null;
        if (StringUtils.hasText(referralCode)) {
            ReferralCode code = referralCodeRepository.findByCodeIgnoreCase(referralCode.trim())
                    .filter(ReferralCode::isActive)
                    .orElseThrow(() -> ApiException.badRequest("Referral code is invalid or expired"));
            if (code.getTrialDays() > 0) {
                referralTrialDays = code.getTrialDays();
            }
            referralRepository.save(Referral.builder()
                    .referrer(code.getUser())
                    .referred(user)
                    .referralCode(code.getCode())
                    .build());
        }
        subscriptionAccessService.assignSignupPlan(user, referralTrialDays);
    }

    private User findByLogin(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmailIgnoreCase(identifier)
                    .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        }
        return userRepository.findByUsernameIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
    }

    private AuthFlowResponse.OtpChallengeView toOtpView(OtpService.IssuedOtp issued) {
        OtpChallenge challenge = issued.challenge();
        int expires = (int) Math.max(1, Duration.between(Instant.now(), challenge.getExpiresAt()).toSeconds());
        return new AuthFlowResponse.OtpChallengeView(
                challenge.getUuid(),
                maskEmail(challenge.getEmail()),
                expires,
                properties.otp().resendSeconds(),
                challenge.getPurpose().name()
        );
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private AuthResponse issueTokens(User user, boolean remember, String deviceName, String ip, String userAgent) {
        String refresh = TokenHasher.randomToken(48);
        Instant expires = Instant.now().plus(properties.jwt().refreshTokenDays(), ChronoUnit.DAYS);
        sessionRepository.save(UserSession.builder()
                .user(user)
                .refreshTokenHash(TokenHasher.sha256(refresh))
                .deviceName(deviceName)
                .ipAddress(ip)
                .userAgent(userAgent)
                .trustedDevice(remember)
                .revoked(false)
                .expiresAt(expires)
                .lastUsedAt(Instant.now())
                .build());
        return tokensFor(user, refresh, expires);
    }

    private AuthResponse tokensFor(User user, String refreshToken, Instant refreshExpires) {
        subscriptionAccessService.ensureTrialOnLogin(user);
        AuthUser principal = AuthUser.from(user);
        String access = jwtService.createAccessToken(principal);
        String businessName = businessRepository.findByUserId(user.getId())
                .map(Business::getBusinessName)
                .orElse(null);
        SubscriptionStatusResponse subscription = subscriptionAccessService.statusOf(user);
        return new AuthResponse(
                access,
                refreshToken,
                "Bearer",
                properties.jwt().accessTokenMinutes() * 60,
                new AuthResponse.UserSummary(
                        user.getUuid(),
                        user.getName(),
                        user.getEmail(),
                        user.getRole().name(),
                        user.isEmailVerified(),
                        businessName,
                        subscription.plan(),
                        subscription.effectiveStatus(),
                        subscription.accessEntitled(),
                        subscription.requiresRecharge(),
                        subscription.endDate(),
                        subscription.daysRemaining(),
                        user.getPreferredLocale() == null || user.getPreferredLocale().isBlank()
                                ? "en"
                                : com.catalogstudio.user.SupportedLocales.normalize(user.getPreferredLocale())
                )
        );
    }
}
