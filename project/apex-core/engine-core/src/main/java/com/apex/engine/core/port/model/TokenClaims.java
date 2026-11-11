package com.apex.engine.core.port.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * The verified claims of a JSON Web Token (JWT). Returned by
 * {@link TokenVerifier#verify(String)} after successful signature and
 * claim validation.
 *
 * <p>Tier 0 value object. Immutable. All fields are defensive-copied.
 */
public record TokenClaims(
        String subject,
        String tenantId,
        Set<String> roles,
        Instant expiresAt,
        String issuer,
        String audience
) {
    public TokenClaims {
        subject = Objects.requireNonNull(subject, "subject");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        issuer = Objects.requireNonNull(issuer, "issuer");
        audience = Objects.requireNonNull(audience, "audience");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("issuer must not be blank");
        }
        if (audience.isBlank()) {
            throw new IllegalArgumentException("audience must not be blank");
        }
    }
}
