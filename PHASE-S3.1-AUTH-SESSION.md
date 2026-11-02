# PHASE-S3.1-AUTH-SESSION.md — Session Package, Part 1 of 5

**Version:** 1.0 · **Part 1 of 5:** reading protocol, context deltas, prerequisite, scope.
Parts: SESSION · POM-AND-TYPES · POLICY · JWT-FILTER · TESTS-CRITERIA,
each `PHASE-S3.1-AUTH-<part>.md` (`AGENT_RULES.md` §2.13).

---
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
