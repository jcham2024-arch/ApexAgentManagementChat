# PHASE-S3.1-AUTH.md

**Version:** 1.0 · **Sub-phase of S3** · **Ends with JWT verification
and RBAC policy proven by tests**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S3.1

Authoritative where they conflict with any prior file. Do not stop
over them.

### 1.1 `jjwt` is the JWT library

`io.jsonwebtoken:jjwt` 0.12.6, Apache 2.0. Three artifacts:
- `jjwt-api` (compile) — interfaces
- `jjwt-impl` (runtime) — implementation
- `jjwt-gson` (runtime) — Gson-based JSON

`jjwt-gson` pulls Gson, which is Apache 2.0 and smaller than Jackson.
`apex-auth` is Tier 2, so Gson is permitted. If `mvn -B
dependency:resolve` fails on `jjwt-gson` at 0.12.6, fall back to
`jjwt-jackson` at the same version (pulls Jackson, also Apache 2.0) and
note the substitution in the commit body. Do not hand-roll JWT parsing
— signature verification and expiry checks have too many edge cases
(`alg: none`, algorithm confusion, timezone handling) for hand-rolled
code to be trusted in a security module.

### 1.2 `apex-chat-common` is added to `apex-auth`

`Principal` uses `TenantId` from `apex-chat-common`. `apex-auth`
already depends on `engine-core`, which transitively provides
`apex-chat-common`. But `Principal` references `TenantId` directly, so
add an explicit compile dependency on `apex-chat-common`.

### 1.3 HMAC-SHA256 (HS256) only

S3.1 verifies HS256 tokens against a shared secret. RS256 with public
keys lands in S6 when the server signs and other services verify.
S3.1's `JwtAuthFilter` takes a `byte[]` secret in its constructor; the
caller (S6) will load it from `SecretsPort`. Tests construct their own
secrets.

### 1.4 Expiry is required

`jjwt` does not require an `exp` claim by default. A token without
`exp` never expires. `JwtAuthFilter` rejects tokens without `exp` with
`AuthException("claim")`. Do not remove this check.

### 1.5 Secret length validated

HS256 secrets must be at least 32 bytes (256 bits). The constructor
throws `IllegalArgumentException` on a shorter secret. Do not weaken
this.

### 1.6 No auth token issuance

S3.1 verifies tokens. It does not issue them. Token issuance is the
job of an external identity provider (OIDC) or, in development, a small
script in `engine-cli` that signs tokens with the same shared secret.
Neither is in scope for S3.1.

### 1.7 No Spring Security

`apex-auth` is Tier 2. It **may** use Spring Security. S3.1 does not.
The `AuthFilter` interface is a plain Java interface. The Spring filter
that wires it into the request chain lands in S6. This keeps `apex-auth`
testable without a servlet container.

### 1.8 Spec not required

Router §4 confirms S3.1 does not need `docs/apex-chat-spec/`. Do not
read spec parts. Do not create spec files.

## 2. Prerequisite

S3.0 merged to `main`. `mvn -B test` green. `RiskLevel.requiresApproval`
exists. `apex-platform` and `apex-auth` module skeletons exist. If not,
stop and report.

## 3. Scope

Deliver:

1. `apex-auth/pom.xml` update (jjwt + apex-chat-common)
2. Eight source files in `apex-auth`
3. Three test classes
4. Updated `package-info.java` and `README.md` for `apex-auth`
5. No `docs/INVARIANTS.md` changes (no named invariant in S3.1)

## 4. Files

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

### 4.9 `JwtAuthFilter.java`

```java
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
```

### 4.10 Update `apex-auth` metadata

**`apex-platform/apex-auth/src/main/java/com/apex/platform/auth/package-info.java`** — replace content:

```java
/**
 * JWT verification, RBAC policy, and authentication filter for the
 * Apex kernel. Tier 2. Depends on jjwt 0.12.6 (HS256 only). Spring
 * Security integration lands in S6. Ports consumed: none. Ports
 * implemented: none.
 */
@NullMarked
package com.apex.platform.auth;

import org.jspecify.annotations.NullMarked;
```

**`apex-platform/apex-auth/README.md`** — replace content:

```markdown
# apex-auth

**Tier:** 2 · **Purpose:** JWT verification, RBAC policy, auth filter.

## Allowed dependencies
any (Tier 2); currently jjwt 0.12.6 (HS256), gson (via jjwt-gson)

## Ports consumed
none

## Ports implemented
none (AuthFilter is a plain interface; Spring wiring lands in S6)

## Design notes
- HS256 only in S3.1. RS256 with public keys lands in S6.
- Tokens must carry `sub`, `tenant_id`, `roles`, and `exp` claims.
- Secret must be at least 32 bytes; shorter secrets are rejected.
- RBAC: 4 roles, 8 permissions, no role inheritance, explicit grants.
- Separation of duties: TENANT_ADMIN does not hold PROPOSAL_APPROVE.

## Tests
PrincipalTest · RbacPolicyTest · JwtAuthFilterTest
```

## 5. Tests

Package `com.apex.platform.auth` (mirror main).

### 5.1 `PrincipalTest.java`

Tests:
1. `acceptsValidPrincipal` — construct, assert all fields
2. `rejectsNullUserId`
3. `rejectsNullTenant`
4. `rejectsNullExpiresAt`
5. `rejectsBlankUserId`
6. `rejectsEmptyRoles`
7. `rolesAreDefensivelyCopied` — mutate input set, assert principal
   unchanged
8. `canDelegatesToPolicy` — operator can CHAT_USE, cannot AUDIT_READ

### 5.2 `RbacPolicyTest.java`

Tests:
1. `operatorCanChatAndUseTools`
2. `operatorCannotAudit`
3. `operatorCannotApproveProposal`
4. `reviewerCanApproveProposal`
5. `reviewerCanReadAudit`
6. `reviewerCannotManageConfig`
7. `tenantAdminCanManageConfig`
8. `tenantAdminCannotApproveProposal` (separation of duties)
9. `systemHasEveryPermission` — iterate `Permission.values()`
10. `multipleRolesUnionPermissions` — operator+reviewer grants both
    role-specific permissions
11. `emptyRolesDenied` — empty role set returns false

### 5.3 `JwtAuthFilterTest.java`

Helper method inside the class:

```java
private static String buildToken(String userId, String tenantId,
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
```

Tests:
1. `validTokenReturnsPrincipal` — token with correct claims returns
   principal
2. `expiredTokenThrows` — category "expired"
3. `wrongSignatureThrows` — token signed with different secret,
   category "signature"
4. `malformedTokenThrows` — `"Bearer not-a-jwt"`, category
   "signature"
5. `missingHeaderThrows` — null, category "missing"
6. `blankHeaderThrows` — `"   "`, category "missing"
7. `noBearerPrefixThrows` — raw token without prefix, category
   "malformed"
8. `emptyTokenAfterBearerThrows` — `"Bearer "`, category "malformed"
9. `missingTenantClaimThrows` — category "claim"
10. `missingRolesClaimThrows` — category "claim"
11. `unknownRoleThrows` — `roles: ["NOT_A_ROLE"]`, category "claim"
12. `missingExpirationThrows` — no `exp` claim, category "claim"
13. `secretTooShortRejected` — `new JwtAuthFilter(new byte[16])` throws
    `IllegalArgumentException`

Use `@BeforeEach` to construct `filter = new JwtAuthFilter(SECRET)`.
`SECRET` is a 32-byte array. `WRONG_SECRET` is a different 32-byte
array.

### 5.4 Line count discipline

Each test class under 200 lines. `JwtAuthFilterTest` will be the
largest; if it approaches 195 lines, split the error-category tests
into `JwtAuthFilterErrorTest` and keep the happy-path + helper in
`JwtAuthFilterTest`. Both must be named in the commit.

## 6. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `PrincipalTest` passes all 8 tests.
- [ ] `RbacPolicyTest` passes all 11 tests.
- [ ] `JwtAuthFilterTest` passes all 13 tests.
- [ ] `apex-auth/pom.xml` declares `jjwt-api` (compile),
      `jjwt-impl` and `jjwt-gson` (runtime), and `apex-chat-common`
      (compile). No other dependency added.
- [ ] No file exceeds 200 lines.
- [ ] `JwtAuthFilter` requires the `exp` claim.
- [ ] `JwtAuthFilter` rejects secrets shorter than 32 bytes.
- [ ] No Spring, Jackson, Micrometer, Google, or Embabel dependency
      added to `apex-auth` (jjwt-gson pulls Gson, which is permitted).
- [ ] No other module modified.
- [ ] No spec files read or created.
- [ ] `docs/INVARIANTS.md` unchanged (S3.1 proves no named invariant;
      K1, K3, K4, K7, E2 are proven in S3.5).

## 7. Do NOT add

- Any Spring Security, Spring Boot, Jackson, Micrometer, or Google
  dependency
- Any JWT issuance, token generation, or signing API in production code
  (test code may sign tokens via `Jwts.builder()`)
- Any user database, session storage, or credential store
- Any RS256 / public-key verification (S6)
- Any HTTP filter implementation for a servlet container (S6)
- Any change to `engine-core`, `engine-planner`, or any Tier 0/Tier 1
  module
- Any spec file
- Any file not listed in §4 or §5

## 8. Commit

Three commits on `phase/s3.1-auth`:

1. `build(S3.1): add jjwt and apex-chat-common to apex-auth`
2. `feat(S3.1): add Role, Permission, Principal, RbacPolicy, JwtAuthFilter`
3. `test(S3.1): add principal, RBAC, and JWT filter tests`

Push. Report CI link, per-test-class pass counts, and confirm:
- `JwtAuthFilterTest.missingExpirationThrows` passed (exp-required
  enforcement)
- `JwtAuthFilterTest.wrongSignatureThrows` passed
- `RbacPolicyTest.tenantAdminCannotApproveProposal` passed (separation
  of duties)
- `RbacPolicyTest.systemHasEveryPermission` passed (all 8 permissions)

If CI fails twice on the same root cause, stop and report the log.

---

**End of S3.1 session package.**