package com.apex.platform.auth;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Default RBAC policy. Each role's permissions are explicit and
 * disjoint from other roles where possible. {@code SYSTEM} holds all
 * permissions. Separation of duties: {@code TENANT_ADMIN} does not
 * hold {@code PROPOSAL_APPROVE} — that is reserved for
 * {@code REVIEWER}.
 */
public final class DefaultRbacPolicy implements RbacPolicy {

    private static final Map<Role, Set<Permission>> GRANTS = Map.of(
            Role.OPERATOR, EnumSet.of(
                    Permission.CHAT_USE,
                    Permission.TOOL_EXECUTE),
            Role.REVIEWER, EnumSet.of(
                    Permission.CHAT_USE,
                    Permission.TOOL_EXECUTE,
                    Permission.TOOL_EXECUTE_HIGH_RISK,
                    Permission.PROPOSAL_APPROVE,
                    Permission.AUDIT_READ),
            Role.TENANT_ADMIN, EnumSet.of(
                    Permission.CHAT_USE,
                    Permission.SESSION_READ_ALL,
                    Permission.CONFIG_MANAGE,
                    Permission.USER_MANAGE),
            Role.SYSTEM, EnumSet.allOf(Permission.class)
    );

    private static final DefaultRbacPolicy INSTANCE = new DefaultRbacPolicy();

    public static DefaultRbacPolicy instance() {
        return INSTANCE;
    }

    @Override
    public boolean allows(Set<Role> roles, Permission permission) {
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(permission, "permission");
        for (Role role : roles) {
            Set<Permission> granted = GRANTS.get(role);
            if (granted != null && granted.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    /** For tests: returns the permissions granted to a single role. */
    public Set<Permission> permissionsFor(Role role) {
        return Collections.unmodifiableSet(
                GRANTS.getOrDefault(role, Set.of()));
    }
}
