package com.apex.platform.auth;

import com.apex.chat.common.identity.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * An authenticated caller. Extracted from a verified JWT. Immutable.
 * {@link #can(RbacPolicy, Permission)} delegates to the supplied
 * policy so the principal does not hard-code role logic.
 */
public record Principal(
        String userId,
        TenantId tenantId,
        Set<Role> roles,
        Instant expiresAt
) {
    public Principal {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        roles = Set.copyOf(roles);
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("roles must not be empty");
        }
    }

    /** True iff at least one of this principal's roles grants the permission. */
    public boolean can(RbacPolicy policy, Permission permission) {
        return policy.allows(roles, permission);
    }
}
