package com.apex.platform.auth;

import com.apex.chat.common.identity.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PrincipalTest {

    private static final Instant EXPIRES = Instant.parse("2030-01-01T00:00:00Z");
    private static final TenantId TENANT = new TenantId("tenant-1");

    private static Principal principal(Set<Role> roles) {
        return new Principal("user-1", TENANT, roles, EXPIRES);
    }

    @Test
    void acceptsValidPrincipal() {
        Principal principal = principal(Set.of(Role.OPERATOR));

        assertThat(principal.userId()).isEqualTo("user-1");
        assertThat(principal.tenantId()).isEqualTo(TENANT);
        assertThat(principal.roles()).containsExactly(Role.OPERATOR);
        assertThat(principal.expiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    void rejectsNullUserId() {
        assertThatNullPointerException().isThrownBy(
                () -> new Principal(null, TENANT, Set.of(Role.OPERATOR), EXPIRES));
    }

    @Test
    void rejectsNullTenant() {
        assertThatNullPointerException().isThrownBy(
                () -> new Principal("user-1", null, Set.of(Role.OPERATOR), EXPIRES));
    }

    @Test
    void rejectsNullExpiresAt() {
        assertThatNullPointerException().isThrownBy(
                () -> new Principal("user-1", TENANT, Set.of(Role.OPERATOR), null));
    }

    @Test
    void rejectsBlankUserId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Principal("  ", TENANT,
                        Set.of(Role.OPERATOR), EXPIRES))
                .withMessage("userId must not be blank");
    }

    @Test
    void rejectsEmptyRoles() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Principal("user-1", TENANT, Set.of(), EXPIRES))
                .withMessage("roles must not be empty");
    }

    @Test
    void rolesAreDefensivelyCopied() {
        Set<Role> mutable = new HashSet<>();
        mutable.add(Role.OPERATOR);

        Principal principal = principal(mutable);
        mutable.add(Role.SYSTEM);

        assertThat(principal.roles()).containsExactly(Role.OPERATOR);
    }

    @Test
    void canDelegatesToPolicy() {
        Principal operator = principal(Set.of(Role.OPERATOR));
        RbacPolicy policy = DefaultRbacPolicy.instance();

        assertThat(operator.can(policy, Permission.CHAT_USE)).isTrue();
        assertThat(operator.can(policy, Permission.AUDIT_READ)).isFalse();
    }
}
