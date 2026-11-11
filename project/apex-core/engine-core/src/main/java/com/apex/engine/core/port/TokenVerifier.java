package com.apex.engine.core.port;

import com.apex.engine.core.port.model.TokenClaims;

/**
 * Verifies a JSON Web Token (JWT) and returns its verified claims.
 *
 * <p>Tier 0 port. Implementations live in adapters (e.g.
 * {@code apex-auth-jwt-asymmetric}). The port contract is intentionally
 * minimal: verify a compact JWT string and return its claims. Signature
 * verification, key resolution, and clock-skew tolerance are all
 * implementation concerns of the adapter.
 *
 * <p>Implementations must be thread-safe: a single verifier instance may
 * be shared across multiple concurrent verification requests.
 */
public interface TokenVerifier {

    /**
     * Verifies the JWT token and returns its verified claims.
     *
     * @param token the JWT compact serialization
     * @return the verified claims
     * @throws TokenVerificationException if the token is invalid for any
     *         reason (bad signature, expired, unknown key, disallowed
     *         algorithm, malformed, etc.)
     */
    TokenClaims verify(String token) throws TokenVerificationException;
}
