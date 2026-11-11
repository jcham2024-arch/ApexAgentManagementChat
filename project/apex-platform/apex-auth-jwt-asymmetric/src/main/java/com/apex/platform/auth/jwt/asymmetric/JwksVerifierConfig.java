package com.apex.platform.auth.jwt.asymmetric;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for {@link JwksTokenVerifier}.
 *
 * <p>All fields are required unless noted otherwise. Instances are
 * immutable.
 */
public record JwksVerifierConfig(
        String jwksUri,
        String issuer,
        String audience,
        Set<String> algorithmAllowlist,
        Duration cacheTtl,
        Duration refreshCooldown,
        byte[] hmacKey
) {

    public JwksVerifierConfig {
        jwksUri = Objects.requireNonNull(jwksUri, "jwksUri");
        issuer = Objects.requireNonNull(issuer, "issuer");
        audience = Objects.requireNonNull(audience, "audience");
        algorithmAllowlist = algorithmAllowlist == null
                ? Set.of("RS256")
                : Set.copyOf(algorithmAllowlist);
        cacheTtl = cacheTtl == null ? Duration.ofHours(24) : cacheTtl;
        refreshCooldown = refreshCooldown == null
                ? Duration.ofMinutes(5) : refreshCooldown;
        hmacKey = hmacKey == null ? null : hmacKey.clone();
    }
}
