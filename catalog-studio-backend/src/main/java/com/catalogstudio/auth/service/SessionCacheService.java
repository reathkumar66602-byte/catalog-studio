package com.catalogstudio.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Redis-backed refresh-session lookaside. Postgres remains source of truth;
 * cache miss falls through to DB in AuthService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionCacheService {

    private static final String KEY_PREFIX = "cs:session:";

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    public void put(String refreshTokenHash, long sessionId, long userId, Instant expiresAt) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null || !StringUtils.hasText(refreshTokenHash) || expiresAt == null) {
            return;
        }
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        try {
            redis.opsForValue().set(
                    KEY_PREFIX + refreshTokenHash,
                    sessionId + ":" + userId,
                    Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException ex) {
            log.warn("Redis session put failed: {}", ex.toString());
        }
    }

    public Optional<CachedSession> get(String refreshTokenHash) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null || !StringUtils.hasText(refreshTokenHash)) {
            return Optional.empty();
        }
        try {
            String raw = redis.opsForValue().get(KEY_PREFIX + refreshTokenHash);
            if (!StringUtils.hasText(raw)) {
                return Optional.empty();
            }
            String[] parts = raw.split(":", 2);
            if (parts.length != 2) {
                return Optional.empty();
            }
            return Optional.of(new CachedSession(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
        } catch (RuntimeException ex) {
            log.warn("Redis session get failed: {}", ex.toString());
            return Optional.empty();
        }
    }

    public void evict(String refreshTokenHash) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null || !StringUtils.hasText(refreshTokenHash)) {
            return;
        }
        try {
            redis.delete(KEY_PREFIX + refreshTokenHash);
        } catch (RuntimeException ex) {
            log.warn("Redis session evict failed: {}", ex.toString());
        }
    }

    public record CachedSession(long sessionId, long userId) {}
}
