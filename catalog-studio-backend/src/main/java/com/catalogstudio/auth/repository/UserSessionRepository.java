package com.catalogstudio.auth.repository;

import com.catalogstudio.auth.entity.UserSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);
    List<UserSession> findByUserIdAndRevokedFalse(Long userId);

    @Modifying
    @Query("update UserSession s set s.revoked = true where s.user.id = :userId and s.revoked = false")
    int revokeAllForUser(Long userId);
}
