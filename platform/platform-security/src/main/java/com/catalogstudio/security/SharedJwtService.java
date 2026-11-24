package com.catalogstudio.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Stateless JWT create/parse used by platform-api and downstream services.
 * Claims include numeric userId so services need no users table.
 */
public class SharedJwtService {

    private final JwtProperties properties;

    public SharedJwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(AuthUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.accessTokenMinutes() * 60);
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", user.uuid().toString());
        claims.put("userId", user.id());
        claims.put("role", user.role());
        return Jwts.builder()
                .subject(user.email())
                .id(UUID.randomUUID().toString())
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public AuthUser toAuthUser(Claims claims) {
        String email = claims.getSubject();
        Object uid = claims.get("uid");
        Object userId = claims.get("userId");
        Object role = claims.get("role");
        if (email == null || uid == null || userId == null) {
            throw new IllegalArgumentException("JWT missing required claims");
        }
        Long id = userId instanceof Number n ? n.longValue() : Long.parseLong(userId.toString());
        return AuthUser.fromClaims(id, UUID.fromString(uid.toString()), email, role == null ? null : role.toString());
    }

    private SecretKey signingKey() {
        byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
