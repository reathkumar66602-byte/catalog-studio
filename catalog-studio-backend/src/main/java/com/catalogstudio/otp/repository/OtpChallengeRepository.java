package com.catalogstudio.otp.repository;

import com.catalogstudio.otp.entity.OtpChallenge;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {
    Optional<OtpChallenge> findByUuid(UUID uuid);
}
