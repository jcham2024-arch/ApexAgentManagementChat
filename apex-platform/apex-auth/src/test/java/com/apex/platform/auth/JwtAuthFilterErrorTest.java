package com.apex.platform.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.apex.platform.auth.JwtAuthFilterTest.FUTURE;
import static com.apex.platform.auth.JwtAuthFilterTest.SECRET;
import static com.apex.platform.auth.JwtAuthFilterTest.TENANT;
import static com.apex.platform.auth.JwtAuthFilterTest.WRONG_SECRET;
import static com.apex.platform.auth.JwtAuthFilterTest.buildToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Token crypto, expiry and claim failures. */
class JwtAuthFilterErrorTest {

    JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(SECRET);
    }

    @Test
    void expiredTokenThrows() {
        String token = buildToken("user-1", TENANT, List.of("OPERATOR"),
                Instant.now().minusSeconds(60), SECRET);

        assertAuthException("Bearer " + token, "expired");
    }

    @Test
    void wrongSignatureThrows() {
        String token = buildToken("user-1", TENANT, List.of("OPERATOR"),
                FUTURE, WRONG_SECRET);

        assertAuthException("Bearer " + token, "signature");
    }

    @Test
    void malformedTokenThrows() {
        assertAuthException("Bearer not-a-jwt", "signature");
    }

    @Test
    void missingTenantClaimThrows() {
        String token = unsignedClaims("user-1", List.of("OPERATOR"), FUTURE,
                true);

        assertAuthException("Bearer " + token, "claim");
    }

    @Test
    void missingRolesClaimThrows() {
        String token = unsignedClaims("user-1", List.of(), FUTURE, false);

        assertAuthException("Bearer " + token, "claim");
    }

    @Test
    void unknownRoleThrows() {
        String token = buildToken("user-1", TENANT, List.of("NOT_A_ROLE"),
                FUTURE, SECRET);

        assertAuthException("Bearer " + token, "claim");
    }

    @Test
    void missingExpirationThrows() {
        String token = Jwts.builder()
                .subject("user-1")
                .claim("tenant_id", TENANT)
                .claim("roles", List.of("OPERATOR"))
                .signWith(Keys.hmacShaKeyFor(SECRET))
                .compact();

        assertAuthException("Bearer " + token, "claim");
    }

    @Test
    void blankSubjectThrows() {
        String token = buildToken("   ", TENANT, List.of("OPERATOR"),
                FUTURE, SECRET);

        assertAuthException("Bearer " + token, "claim");
    }

    @Test
    void secretTooShortRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtAuthFilter(new byte[16]))
                .withMessageContaining("at least 32 bytes");
    }

    /** Builds a token with a selectable claim shape. */
    private static String unsignedClaims(String userId, List<String> roles,
                                         Instant expiresAt,
                                         boolean omitTenant) {
        var builder = Jwts.builder().subject(userId);
        if (!omitTenant) {
            builder.claim("tenant_id", TENANT);
        }
        if (!roles.isEmpty()) {
            builder.claim("roles", roles);
        }
        builder.expiration(Date.from(expiresAt));
        return builder.signWith(Keys.hmacShaKeyFor(SECRET)).compact();
    }

    private void assertAuthException(String header, String category) {
        assertThatThrownBy(() -> filter.authenticate(header))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).category())
                        .isEqualTo(category));
    }
}
