package com.apex.platform.auth.jwt.asymmetric;

import com.apex.engine.core.port.TokenVerificationException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;

import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Helper methods for {@link JwksTokenVerifier}: key resolution,
 * verifier construction, and claim validation.
 */
final class JwksTokenVerifierHelper {

    private JwksTokenVerifierHelper() {
    }

    static PublicKey resolveKey(JwksKeySource keySource, String kid,
            String alg) throws TokenVerificationException {
        if (kid == null || kid.isBlank()) {
            throw new TokenVerificationException(
                    "key", "missing 'kid' in token header");
        }

        Optional<PublicKey> key = keySource.getKey(kid);
        if (key.isEmpty()) {
            throw new TokenVerificationException(
                    "key", "key not found for kid: " + kid);
        }

        PublicKey publicKey = key.get();

        if ("RS256".equals(alg) && !(publicKey instanceof RSAPublicKey)) {
            throw new TokenVerificationException(
                    "algorithm", "key for kid '" + kid
                            + "' is not an RSA public key");
        }

        return publicKey;
    }

    static JWSVerifier buildVerifier(PublicKey key, String alg)
            throws JOSEException {
        if ("RS256".equals(alg)) {
            return new RSASSAVerifier((RSAPublicKey) key);
        }
        throw new JOSEException("unsupported algorithm: " + alg);
    }

    static void validateClaims(JWTClaimsSet claims, String issuer,
            String audience, Duration clockSkewTolerance)
            throws TokenVerificationException {
        Instant now = Instant.now();

        String issuerClaim = claims.getIssuer();
        if (issuer != null && !issuer.equals(issuerClaim)) {
            throw new TokenVerificationException(
                    "issuer", "issuer mismatch: expected '"
                            + issuer + "', got '" + issuerClaim + "'");
        }

        List<String> aud = claims.getAudience();
        if (aud == null || aud.isEmpty() || !aud.contains(audience)) {
            throw new TokenVerificationException(
                    "audience", "audience mismatch: expected '"
                            + audience + "'");
        }

        Date exp = claims.getExpirationTime();
        if (exp == null) {
            throw new TokenVerificationException(
                    "claim", "missing 'exp' claim");
        }
        Instant expInstant = exp.toInstant();
        if (expInstant.isBefore(now.minus(clockSkewTolerance))) {
            throw new TokenVerificationException(
                    "expired", "token expired at " + expInstant);
        }

        Date nbf = claims.getNotBeforeTime();
        if (nbf != null) {
            Instant nbfInstant = nbf.toInstant();
            if (nbfInstant.isAfter(now.plus(clockSkewTolerance))) {
                throw new TokenVerificationException(
                        "claim", "token not yet valid until " + nbfInstant);
            }
        }
    }
}
