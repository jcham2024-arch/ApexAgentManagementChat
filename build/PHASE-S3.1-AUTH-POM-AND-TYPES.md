# PHASE-S3.1-AUTH-POM-AND-TYPES.md — Session Package, Part 2 of 5

**Version:** 1.0 · **Part 2 of 5:** §4.1–§4.4, pom, Role, Permission, Principal.
Parts: SESSION · POM-AND-TYPES · POLICY · JWT-FILTER · TESTS-CRITERIA,
each `PHASE-S3.1-AUTH-<part>.md` (`AGENT_RULES.md` §2.13).

---
### 4.1 `apex-platform/apex-auth/pom.xml` — add dependencies

Insert inside `<dependencies>`, after the existing `engine-core`
dependency:

```xml
<dependency>
    <groupId>com.apex</groupId>
    <artifactId>apex-chat-common</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-gson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

If `jjwt-gson` does not resolve, replace the third block with
`jjwt-jackson` at the same version and note the substitution. Do not
add both.

### 4.2 `Role.java`

```java
package com.apex.platform.auth;

/**
 * Authorisation roles. Each role is a distinct capability bundle; no
 * role inherits from another. A principal may carry multiple roles;
 * effective permissions are the union.
 */
public enum Role {
    /** Normal user: chats, runs tools, sees own sessions. */
    OPERATOR,
    /** HITL approver: reviews proposals, reads audit, approves L3+. */
    REVIEWER,
    /** Tenant administrator: manages config, users, sees all sessions. */
    TENANT_ADMIN,
    /** Internal system principal: all permissions. */
    SYSTEM
}
```

### 4.3 `Permission.java`

```java
package com.apex.platform.auth;

/**
 * Capability strings checked by {@link RbacPolicy}. Each permission is
 * a coherent action; do not create permissions for individual tool
 * names — tool-level allowlisting lives in the tool registry, not in
 * RBAC.
 */
public enum Permission {
    /** Send messages and view own sessions. */
    CHAT_USE,
    /** Execute L0–L2 tools. */
    TOOL_EXECUTE,
    /** Execute L3+ tools. Requires explicit approval per call. */
    TOOL_EXECUTE_HIGH_RISK,
    /** Approve flywheel proposals and HITL requests. */
    PROPOSAL_APPROVE,
    /** Read the audit log. */
    AUDIT_READ,
    /** Manage provider config, tenant settings. */
    CONFIG_MANAGE,
    /** Invite, remove, and change roles of users in the tenant. */
    USER_MANAGE,
    /** Read other users' sessions in the same tenant. */
    SESSION_READ_ALL
}
```

### 4.4 `Principal.java`

```java
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
```
