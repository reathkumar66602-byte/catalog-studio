package com.catalogstudio.otp.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.common.util.TokenHasher;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.email.service.TemplatedEmailService;
import com.catalogstudio.otp.entity.OtpChallenge;
import com.catalogstudio.otp.repository.OtpChallengeRepository;
import com.catalogstudio.user.entity.User;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository challengeRepository;
    private final TemplatedEmailService templatedEmailService;
    private final CatalogStudioProperties properties;

    @Transactional
    public IssuedOtp issue(User user, OtpChallenge.Purpose purpose, boolean rememberDevice, String deviceName,
                              String ip, String userAgent) {
        CatalogStudioProperties.Otp otp = properties.otp();
        String code = generateCode(otp.length());
        OtpChallenge challenge = challengeRepository.save(OtpChallenge.builder()
                .user(user)
                .email(user.getEmail())
                .purpose(purpose)
                .codeHash(TokenHasher.sha256(code))
                .expiresAt(Instant.now().plus(otp.ttlMinutes(), ChronoUnit.MINUTES))
                .attempts(0)
                .maxAttempts(otp.maxAttempts())
                .rememberDevice(rememberDevice)
                .deviceName(deviceName)
                .ipAddress(ip)
                .userAgent(userAgent)
                .lastSentAt(Instant.now())
                .build());
        send(challenge, user, code);
        return new IssuedOtp(challenge, code);
    }

    @Transactional
    public IssuedOtp resendIssued(UUID challengeId) {
        OtpChallenge challenge = requireOpen(challengeId);
        CatalogStudioProperties.Otp otp = properties.otp();
        Instant earliest = challenge.getLastSentAt().plusSeconds(otp.resendSeconds());
        if (Instant.now().isBefore(earliest)) {
            long wait = Math.max(1, Duration.between(Instant.now(), earliest).toSeconds());
            throw ApiException.tooManyRequests("Please wait " + wait + " seconds before requesting another OTP");
        }
        String code = generateCode(otp.length());
        challenge.setCodeHash(TokenHasher.sha256(code));
        challenge.setExpiresAt(Instant.now().plus(otp.ttlMinutes(), ChronoUnit.MINUTES));
        challenge.setAttempts(0);
        challenge.setLastSentAt(Instant.now());
        send(challenge, challenge.getUser(), code);
        return new IssuedOtp(challenge, code);
    }

    @Transactional
    public OtpChallenge resend(UUID challengeId) {
        return resendIssued(challengeId).challenge();
    }

    @Transactional
    public OtpChallenge consume(UUID challengeId, String code) {
        OtpChallenge challenge = requireOpen(challengeId);
        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            throw ApiException.badRequest("Too many incorrect OTP attempts. Request a new code");
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (code == null || !challenge.getCodeHash().equals(TokenHasher.sha256(code.trim()))) {
            throw ApiException.badRequest("Invalid OTP");
        }
        challenge.setConsumedAt(Instant.now());
        return challenge;
    }

    private OtpChallenge requireOpen(UUID challengeId) {
        OtpChallenge challenge = challengeRepository.findByUuid(challengeId)
                .orElseThrow(() -> ApiException.badRequest("OTP session is invalid"));
        if (challenge.isConsumed()) {
            throw ApiException.badRequest("OTP has already been used");
        }
        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("OTP has expired. Request a new code");
        }
        return challenge;
    }

    private void send(OtpChallenge challenge, User user, String code) {
        if (properties.otp().logCode()) {
            log.info("OTP for {} [{}] is {}", challenge.getEmail(), challenge.getPurpose(), code);
        }
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("otp", code);
        vars.put("name", user.getName());
        vars.put("username", user.getUsername() == null ? user.getName() : user.getUsername());
        vars.put("email", user.getEmail());
        vars.put("appName", "Catalog Studio");
        vars.put("expiresMinutes", String.valueOf(properties.otp().ttlMinutes()));
        String slug = switch (challenge.getPurpose()) {
            case REGISTER -> "otp-register";
            case LOGIN -> "otp-login";
            case VERIFY_EMAIL -> "otp-verify-email";
        };
        boolean sent = templatedEmailService.send(slug, user.getEmail(), vars);
        if (!sent) {
            log.error("OTP email was not delivered to {} via {}", user.getEmail(), properties.mail().resolvedHost());
            throw ApiException.unavailable("We could not send the verification email. Please try again in a minute.");
        }
        log.info("OTP email queued for {} [{}]", challenge.getEmail(), challenge.getPurpose());
    }

    private String generateCode(int length) {
        int digits = Math.max(4, Math.min(length, 8));
        int bound = (int) Math.pow(10, digits);
        int min = (int) Math.pow(10, digits - 1);
        return String.valueOf(min + RANDOM.nextInt(bound - min));
    }

    public record IssuedOtp(OtpChallenge challenge, String code) {}
}
