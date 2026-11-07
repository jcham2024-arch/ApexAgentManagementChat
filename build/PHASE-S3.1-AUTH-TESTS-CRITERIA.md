# PHASE-S3.1-AUTH-TESTS-CRITERIA.md — Session Package, Part 5 of 5

**Version:** 1.0 · **Part 5 of 5:** §5–§8, tests, exit criteria, commits.
Parts: SESSION · POM-AND-TYPES · POLICY · JWT-FILTER · TESTS-CRITERIA,
each `PHASE-S3.1-AUTH-<part>.md` (`AGENT_RULES.md` §2.13).

---
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
