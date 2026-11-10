# BUILD-SPEC: Asymmetric JWT / RS256 with JWKS (Auth)

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
- **Chain format**: N/A (JWT is not a hash chain).
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

## 7. Determinism Impact

**No impact on Tier 0 determinism.** `apex-auth` is Tier 2 and does not
participate in plan generation or audit ordering. JWT verification is
deterministic given the same token and the same key set — identical inputs
produce identical verification results.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **RS256 public keys** | Exposed at JWKS endpoint; only public key material, never HS256 |
| | shared secrets. HS256 secret never exposed via JWKS. |
| **Key location** | Verifier-side key management is out of scope for v1; the shared |
| | secret and JWKS endpoint URL are configuration inputs. |
| **Algorithm substitution** | Explicit allowlist (`HS256`, `RS256`); `none` rejected; |
| | substitution attempts rejected with `AuthException("signature", ...)`. |
| **DoS via random `kid`** | JWKS refresh rate-limited to once per 5 minutes. |
| **Clock skew** | ±30 second tolerance on `exp`/`nbf`, configurable. |
| **PII** | JWT claims (`sub`, `tenant_id`, `roles`) are not PII-redacted by the |
| | verifier; redaction is the issuer's responsibility. |

## 9. Audit Impact

Every authentication attempt (success or failure) is a candidate for audit
logging. The `AuthFilter` itself does not write to the audit chain — the
caller (Spring filter or application code) is responsible for auditing
authentication outcomes through `AuditPort`. This spec does not add audit
calls to `JwtAuthFilter` to keep the filter focused on verification.

## 10. Failure, Cancellation, and Timeout Semantics

| Scenario | Required Behavior |
| :--- | :--- |
| **JWKS endpoint unreachable** | Cached keys remain valid; on `kid` miss with stale cache, reject |
| | with `AuthException("signature", "key unavailable")`. |
| **JWKS returns invalid JSON** | Treat as key fetch failure; fall back to cached keys; |
| | reject on `kid` miss. |
| **Expired token** | `AuthException("expired", "token expired")`. |
| **Invalid signature** | `AuthException("signature", ...)`. |
| **Missing `kid` on RS256 token** | `AuthException("signature", "missing kid")`. |
| **`alg: none`** | `AuthException("signature", "algorithm none not allowed")`. |
| **Algorithm substitution** | Rejected by allowlist; `AuthException("signature", ...)`. |
| **Rate-limit exceeded** | `AuthException("signature", "key refresh rate-limited")`. |

Cancellation tokens are not part of the `AuthFilter` contract; verification is
synchronous and fast (local key lookup or bounded JWKS fetch).

## 11. Test Plan

### 11.1 Unit tests

- HS256 token verifies with shared secret (regression).
- HS256 token with wrong secret fails with `AuthException("signature", ...)`.
- RS256 token verifies with cached public key selected by `kid`.
- RS256 token with unknown `kid` triggers lazy JWKS refresh, then verifies
  when the key is published.
- RS256 token with `kid` not in JWKS (after refresh) fails with
  `AuthException("signature", "key not found")`.
- Token with `alg: none` rejected with `AuthException("signature", ...)`.
- Token with unsupported algorithm (e.g. `ES256`) rejected.
- Expired token rejected with `AuthException("expired", ...)`.
- Token missing required claims (`sub`, `tenant_id`, `roles`, `exp`) rejected
  with `AuthException("claim", ...)`.
- Clock skew tolerance: token with `exp` 29 seconds in the past verifies;
  token with `exp` 31 seconds in the past fails (configurable boundary).
- Rate limiting: rapid `kid` misses are throttled to one JWKS fetch per
  5 minutes.

### 11.2 Integration tests

- **Cache behavior:** token signed with key A verifies; key B published to
  JWKS; token signed with key B verifies after cache TTL expiry or lazy
  refresh; token signed with key A still verifies during overlap window
  (make-before-break).
- **Graceful overlap:** token signed by retiring key still verifies after new
  key is published; old key dropped after grace window.
- **Make-before-break:** no period where valid tokens from both old and new
  keys fail simultaneously.
- **Concurrent verification:** multiple threads verifying tokens against the
  same `JwtAuthFilter` instance; no key cache corruption.

### 11.3 Architectural enforcement

- No Tier 0 module gains a new dependency; `CorePurityTest` passes.
- `apex-auth` remains Tier 2; jjwt remains a Tier 2 dependency only.
- `AuthFilter` signature unchanged — no breaking change.

### 11.4 Coverage targets

- All `AuthFilter.authenticate` code paths exercised (HS256 success/failure,
  RS256 success/failure, `none` rejection, expired, missing claims).
- JWKS cache hit/miss/refresh paths exercised.
- Rate-limit path exercised.
- Algorithm allowlist rejection paths exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. `apex-auth` builds under `mvn -B -f project/apex/pom.xml verify`.
2. `AuthFilter.authenticate(String)` signature is unchanged.
3. HS256 tokens verify with the shared secret (regression).
4. RS256 tokens verify against a JWKS-published public key selected by `kid`.
5. Token with `alg: none` is rejected.
6. Token with unsupported algorithm is rejected.
7. Token signed with a key not in the cache triggers exactly one JWKS
   refresh and then verifies when the key is published.
8. Token signed with a retired-but-in-grace key still verifies.
9. Token signed with a key not in the JWKS set (after refresh) is rejected.
10. Clock skew tolerance applied to `exp`/`nbf` (configurable boundary).
11. JWKS refresh rate-limited to once per 5 minutes.
12. `CorePurityTest` passes in all Tier 0 modules (no Tier 0 changes).
13. No new dependencies beyond jjwt (already present) and `java.base`.

## 13. Rollback / Disable Strategy

- **Feature flag / configuration:** RS256 verification is activated only when
  a `JwksProvider` is supplied to `JwtAuthFilter`. HS256-only operation is
  preserved by constructing `JwtAuthFilter` with only the shared secret.
- **Rollback:** If RS256 support causes issues, revert to constructing
  `JwtAuthFilter` with only the HS256 secret. No Tier 0 code changes
  required; `AuthFilter` signature unchanged.
- **Data migration:** Not applicable — JWTs are stateless; no stored state
  to migrate.

## 14. Out of Scope (Explicit)

- JWKS endpoint hosting / key publishing (issuer-side concern, not verifier).
- HMAC key rotation for HS256 shared secret (operational concern).
- Refresh token issuance / storage (application-layer concern).
- Token revocation / blocklist (future work).
- `kid` confusion attack mitigations beyond key selection by `kid` and
  algorithm allowlist (covered by allowlist + `kid` matching).

## 15. Governance References

| Artifact | Path |
| :--- | :--- |
| Spike finding | `docs/spikes/A2-asymmetric-jwt.md` |
| Governing ADR | `docs/adr/ADR-002-jwt-algorithm-policy.md` |
| Decision note | `docs/contracts/decisions/Q4-Q5-jwt-rs256-jwks.md` |
| Port contract inventory | `docs/contracts/ports.md` (AuthFilter row, Tier 2) |
| NFR capture | `docs/contracts/nfr.md` §2 |
| Open questions | `docs/contracts/open-questions.md` (Q4, Q5 closed) |
| Spec readiness checklist | `docs/specs/README.md` |

**Decision governance:** Q4/Q5 (RS256 default, JWKS + kid, make-before-break
rotation, 90-day cadence, short-lived tokens, algorithm allowlist) are closed
and linked above. These decisions constrain this spec as stated in each
decision note's "Constraint on future specs" section.
