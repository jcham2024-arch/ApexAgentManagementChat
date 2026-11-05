# PHASE-S3.1-AUTH-POLICY.md — Session Package, Part 3 of 5

**Version:** 1.0 · **Part 3 of 5:** §4.5–§4.8, AuthException, RbacPolicy, DefaultRbacPolicy, AuthFilter.
Parts: SESSION · POM-AND-TYPES · POLICY · JWT-FILTER · TESTS-CRITERIA,
each `PHASE-S3.1-AUTH-<part>.md` (`AGENT_RULES.md` §2.13).

---
### 4.5 `AuthException.java`

```java
package com.apex.platform.auth;

/**
 * Authentication or authorisation failure. Categories: {@code missing},
 * {@code malformed}, {@code signature}, {@code expired}, {@code claim}.
 */
public final class AuthException extends RuntimeException {
    private final String category;

    public AuthException(String category, String message) {
        super(message);
        this.category = category;
    }

    public AuthException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
```

### 4.6 `RbacPolicy.java`

```java
package com.apex.platform.auth;

import java.util.Set;

/**
 * Answers: does any of these roles grant this permission? A principal
 * with multiple roles has the union of their permissions. No role
 * inherits from another.
 */
public interface RbacPolicy {
    boolean allows(Set<Role> roles, Permission permission);
}
```

### 4.7 `DefaultRbacPolicy.java`

```java
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
```

### 4.8 `AuthFilter.java`

```java
package com.apex.platform.auth;

/**
 * Extracts and verifies an authentication token from an authorization
 * header. Returns a {@link Principal} on success, throws
 * {@link AuthException} on any failure. The server (S6) wires this
 * into a Spring filter.
 */
public interface AuthFilter {
    Principal authenticate(String authorizationHeader) throws AuthException;
}
```
