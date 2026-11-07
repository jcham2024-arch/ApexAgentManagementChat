package com.apex.platform.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Happy path and authorization-header handling. Token crypto and claim
 * failures live in JwtAuthFilterErrorTest so both classes stay well
 * under the 200-line limit, which S3.1 5.4 allows.
 */
class JwtAuthFilterTest {

    static final byte[] SECRET =
            "0123456789abcdef0123456789abcdef".getBytes();
    static final byte[] WRONG_SECRET =
            "fedcba9876543210fedcba9876543210".getBytes();
    static final String TENANT = "tenant-1";
    static final Instant FUTURE = Instant.now().plusSeconds(3600);

    JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(SECRET);
    }

    @Test
    void validTokenReturnsPrincipal() {
        String token = buildToken("user-1", TENANT, List.of("OPERATOR"),
                FUTURE, SECRET);

        Principal principal = filter.authenticate("Bearer " + token);

        assertThat(principal.userId()).isEqualTo("user-1");
        assertThat(principal.tenantId().value()).isEqualTo(TENANT);
        assertThat(principal.roles()).containsExactly(Role.OPERATOR);
        assertThat(principal.expiresAt()).isNotNull();
    }

    @Test
    void multipleRolesParsed() {
        String token = buildToken("user-1", TENANT,
                List.of("OPERATOR", "REVIEWER"), FUTURE, SECRET);

        assertThat(filter.authenticate("Bearer " + token).roles())
                .containsExactlyInAnyOrder(Role.OPERATOR, Role.REVIEWER);
    }

    @Test
    void missingHeaderThrows() {
        assertAuthException(null, "missing");
    }

    @Test
    void blankHeaderThrows() {
        assertAuthException("   ", "missing");
    }

    @Test
    void noBearerPrefixThrows() {
        String token = buildToken("user-1", TENANT, List.of("OPERATOR"),
                FUTURE, SECRET);

        assertAuthException(token, "malformed");
    }

    @Test
    void emptyTokenAfterBearerThrows() {
        assertAuthException("Bearer ", "malformed");
    }

    static String buildToken(String userId, String tenantId,
                             List<String> roles, Instant expiresAt,
                             byte[] secret) {
        return Jwts.builder()
                .subject(userId)
                .claim("tenant_id", tenantId)
                .claim("roles", roles)
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(secret))
                .compact();
    }

    private void assertAuthException(String header, String category) {
        assertThatThrownBy(() -> filter.authenticate(header))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).category())
                        .isEqualTo(category));
    }
}
