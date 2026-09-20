package com.catalogstudio.security;

import com.catalogstudio.config.CatalogStudioProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final CatalogStudioProperties properties;

    public JwtService(CatalogStudioProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(AuthUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.jwt().accessTokenMinutes() * 60);
        return Jwts.builder()
                .subject(user.email())
                .id(UUID.randomUUID().toString())
                .claims(Map.of(
                        "uid", user.uuid().toString(),
                        "role", user.role()
                ))
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

    private SecretKey signingKey() {
        byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
