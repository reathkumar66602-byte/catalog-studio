package com.catalogstudio.marketplace.meesho;

import java.time.Instant;
import java.util.Map;

/** Authenticated Meesho supplier-panel session (cookie / token bundle). */
public record MeeshoPanelSession(
        String email,
        String accessToken,
        String cookieHeader,
        Instant expiresAt,
        Map<String, String> attributes,
        boolean verified
) {
    public static MeeshoPanelSession unverified(String email) {
        return new MeeshoPanelSession(email, null, null, Instant.now().plusSeconds(3600), Map.of(), false);
    }
}
