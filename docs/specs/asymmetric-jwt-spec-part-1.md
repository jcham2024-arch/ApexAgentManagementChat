# BUILD-SPEC: Asymmetric JWT / RS256 with JWKS (Auth) — Part 1 of 2

**Source:** docs/specs/asymmetric-jwt-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 1 of 2
**Cross-reference:** See `asymmetric-jwt-spec-part-2.md` for sections 7-15.

## 1. Module Identity

| Field | Value |
| :--- | :--- |
| **Module name** | `apex-auth` (existing Tier 2 module, modified) |
| **Repository location** | `project/apex-platform/apex-auth/` |
| **Parent aggregator** | `project/apex-platform/pom.xml` |
| **Tier** | **Tier 2** (platform services — allowed external libraries) |
| **Governed by** | ADR-002 (JWT algorithm policy) |
| **Spike input** | `docs/spikes/A2-asymmetric-jwt.md` |
| **Decision input** | `docs/contracts/decisions/Q4-Q5-jwt-rs256-jwks.md` |
| **Spec readiness** | **READY** — Q4/Q5 closed, ADR-002 accepted, port classification additive |

## 2. Purpose

Extend `apex-auth` to support **RS256** as the default signing algorithm for
issuer-signed JWTs, while retaining **HS256** for backward compatibility behind
the same `AuthFilter` port. Public keys are exposed at a JWKS endpoint and
fetched by a remote JWKS client with in-memory caching, lazy refresh on `kid`
mismatch, and rate-limited on-demand refresh. This enables asymmetric
verification (verifier holds only a public key) without breaking existing HS256
callers.

## 3. Ports Consumed and Provided

### Consumed (existing contracts)

- **`AuthFilter`** — `com.apex.platform.auth.AuthFilter`
  - `Principal authenticate(String authorizationHeader)`
  - Tier 2 port (apex-auth internal; not a Tier 0 engine port)
  - Implementation: `JwtAuthFilter` (HS256, S3)

- **`AuthException`** — `com.apex.platform.auth.AuthException`
  - Categories: `missing`, `malformed`, `signature`, `expired`, `claim`
  - Used for all authentication failures

### Provided (modified implementations)

- **`JwtAuthFilter`** — extended to support both HS256 and RS256:
  - HS256 tokens validated against shared secret (existing behavior)
  - RS256 tokens validated against JWKS-published public key, selected by `kid`
  - No change to `AuthFilter.authenticate(String)` signature — **additive**

### Port classification

- **`AuthFilter`**: **additive** — no method signatures changed; new algorithm
  support is internal to the implementation.
- **No new Tier 0 ports introduced.** `AuthFilter` lives in Tier 2 (`apex-auth`),
  so no Tier 0 purity impact.

## 4. Public API Surface and Compatibility

### Java API

No public API changes to `AuthFilter`, `Principal`, or `AuthException`. The
`JwtAuthFilter` constructor may accept an optional `JwksProvider` for RS256
verification; when absent, HS256-only behavior is preserved.

```java
// Existing constructor (unchanged, HS256 only)
public JwtAuthFilter(byte[] secret)

// New constructor (additive, RS256 + HS256)
public JwtAuthFilter(byte[] hs256Secret, JwksProvider jwksProvider)
```

### Compatibility statement

- **`AuthFilter.authenticate(String)`**: unchanged — callers unaffected.
- **`Principal` record**: unchanged.
- **`AuthException` categories**: unchanged; new `signature` sub-cases for
  RS256 verification failure (key not found, invalid signature, expired key).
- **Backward compatibility:** HS256 tokens continue to verify with the shared
  secret. RS256 tokens are additive.

## 5. Dependency Ceiling and Justification

| Dependency | Scope | Justification |
| :--- | :--- | :--- |
| `io.jsonwebtoken:jjwt-api:0.12.6` | Compile | Existing HS256 JWT API (already a dependency). |
| `io.jsonwebtoken:jjwt-impl:0.12.6` | Runtime | Existing HS256 JWT implementation (already a dependency). |
| `io.jsonwebtoken:jjwt-gson:0.12.6` | Runtime | Existing JSON serializer for JWT (already a dependency). |
| `java.base` | Compile/runtime | Required baseline. |
| `jspecify` | Compile | Null-safety annotations per project standards. |
| `org.junit.jupiter:junit-jupiter` | Test | Test framework per project standard. |
| `org.assertj:assertj-core` | Test | Assertions per project standard. |
| `com.tngtech.archunit:archunit-junit5` | Test | Architectural enforcement per project standard. |

**No new dependencies required.** jjwt 0.12.6 already supports RS256 via
`Keys.publicKey(...)` and `Jwts.parser().verifyWith(publicKey)`. JWKS fetching
is implemented with `java.net.http.HttpClient` (Java 11+, in `java.base`).

**Tier 0 purity:** `apex-auth` is Tier 2 — external libraries are allowed.
No Tier 0 module is modified.

## 6. Implementation Design

### 6.1 Algorithm selection

The `JwtAuthFilter` inspects the JWT `alg` header:
- `HS256` → validate with the shared secret (existing path).
- `RS256` → validate against the JWKS-cached public key selected by `kid`.
- Any other algorithm (including `none`) → reject with `AuthException("signature", ...)`.

An explicit algorithm allowlist (`HS256`, `RS256`) prevents algorithm
substitution attacks.

### 6.2 JWKS fetch and cache

A `JwksProvider` (package-private implementation in `apex-auth`) fetches the
JWKS endpoint on a schedule (TTL-based cache, default 24 hours) and caches the
`kid` → `RSAPublicKey` map in memory.

- **Cache hit:** return the cached public key immediately.
- **Cache miss / `kid` mismatch:** trigger a lazy one-time refresh of the
  JWKS endpoint, then retry once. If still missing, reject with
  `AuthException("signature", "key not found")`.
- **Rate limiting:** on-demand JWKS fetches are rate-limited to once per 5
  minutes to prevent DoS via random `kid` values.
- **Rate-limit exceeded during miss:** reject with
  `AuthException("signature", "key refresh rate-limited")`.

### 6.3 Graceful overlap (make-before-break rotation)

The verifier accepts **any valid key currently present in the JWKS set**.
Rotation is make-before-break: the new key is published to the JWKS endpoint
before the old key is retired. During the overlap window, tokens signed by
either key verify successfully. The old key is dropped only after the grace
window has passed.

### 6.4 Rotation cadence

**90-day signing key rotation**, per NIST IR 8587 finalized guidance
(one-hour token lifetime guidance, 90-day signing key rotation). The key
rotation itself is an operational concern (the issuer publishes new keys to
the JWKS endpoint); the verifier picks up the new key via cache TTL expiry
or lazy refresh on `kid` mismatch.

### 6.5 Token lifetime

**Short-lived access tokens: 5–15 minutes** with refresh-token rotation, per
industry-standard JWT practice. The verifier enforces `exp` (jjwt does this
automatically). Tokens with `exp` in the past are rejected with
`AuthException("expired", ...)`.

### 6.6 Clock skew

A small clock-skew tolerance (default ±30 seconds, configurable) is applied
to `exp`/`nbf`. No tolerance on `iat` beyond a small window to reject
obviously-future tokens.
