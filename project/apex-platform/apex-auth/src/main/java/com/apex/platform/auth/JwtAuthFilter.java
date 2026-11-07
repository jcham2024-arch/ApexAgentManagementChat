package com.apex.platform.auth;

import com.apex.chat.common.identity.TenantId;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * HMAC-SHA256 (HS256) JWT verifier. Parses the Bearer token, verifies
 * the signature against a shared secret, checks the expiry (jjwt does
 * this automatically), and extracts the principal.
 *
 * <p>Required claims: {@code sub} (userId), {@code tenant_id},
 * {@code roles} (array of Role names), {@code exp}. Missing or unknown
 * values produce {@code AuthException("claim")}.
 *
 * <p>The secret must be at least 32 bytes. RS256 with public keys
 * lands in S6 when the server signs tokens.
 */
public final class JwtAuthFilter implements AuthFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey key;

    public JwtAuthFilter(byte[] secret) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length < 32) {
            throw new IllegalArgumentException(
                    "secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    @Override
    public Principal authenticate(String authorizationHeader) {
        String token = extractBearer(authorizationHeader);
        return verifyToken(token);
    }

    private String extractBearer(String header) {
        if (header == null || header.isBlank()) {
            throw new AuthException("missing", "no authorization header");
        }
        if (!header.startsWith(BEARER_PREFIX)) {
            throw new AuthException("malformed",
                    "header must start with 'Bearer '");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new AuthException("malformed", "empty token");
        }
        return token;
    }

    private Principal verifyToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new AuthException("expired", "token expired", e);
        } catch (JwtException e) {
            throw new AuthException("signature", e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new AuthException("malformed", e.getMessage(), e);
        }
        return buildPrincipal(claims);
    }

    private Principal buildPrincipal(Claims claims) {
        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new AuthException("claim", "missing 'sub' claim");
        }
        String tenantId = claims.get("tenant_id", String.class);
        if (tenantId == null || tenantId.isBlank()) {
            throw new AuthException("claim", "missing 'tenant_id' claim");
        }
        List<?> rolesRaw = claims.get("roles", List.class);
        if (rolesRaw == null || rolesRaw.isEmpty()) {
            throw new AuthException("claim", "missing 'roles' claim");
        }
        Set<Role> roles = new HashSet<>();
        for (Object raw : rolesRaw) {
            try {
                roles.add(Role.valueOf(String.valueOf(raw)));
            } catch (IllegalArgumentException e) {
                throw new AuthException("claim",
                        "unknown role: " + raw, e);
            }
        }
        if (claims.getExpiration() == null) {
            throw new AuthException("claim", "missing 'exp' claim");
        }
        return new Principal(userId, new TenantId(tenantId), roles,
                claims.getExpiration().toInstant());
    }
}
