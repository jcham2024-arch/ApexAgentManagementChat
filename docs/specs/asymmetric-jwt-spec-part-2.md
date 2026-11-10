# BUILD-SPEC: Asymmetric JWT / RS256 with JWKS (Auth) — Part 2 of 2

**Source:** docs/specs/asymmetric-jwt-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 2 of 2
**Cross-reference:** See `asymmetric-jwt-spec-part-1.md` for sections 1-6.

## 7. Determinism Impact

**No impact on Tier 0 determinism.** `apex-auth` is Tier 2 and does not
participate in plan generation or audit ordering. JWT verification is
deterministic given the same token and the same key set — identical inputs
produce identical verification results.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **HMAC key handling** | The key is supplied to the adapter at construction time. It must |
| | be at least 32 bytes (existing requirement). Key storage, rotation, and |
| | retrieval are **out of scope** for this spec (open question Q3 is |
| | non-blocking; rotation is future work). |
| **PII** | The port contract mandates redaction before hashing[reference:10]. |
| | No additional PII handling is introduced. |
| **Key location** | Verifier-side key management is out of scope for v1; the shared |
| | secret and JWKS endpoint URL are configuration inputs. |

## 9. Audit Impact

Every authentication attempt (success or failure) is a candidate for audit
logging. The `AuthFilter` itself does not write to the audit chain — the
caller (Spring filter or application code) is responsible for auditing
authentication outcomes through `AuditPort`. This spec does not add audit
calls to `JwtAuthFilter` to keep the filter focused on verification.

## 10. Failure, Cancellation, and Timeout Semantics

| Scenario | Required Behavior |
| :--- | :--- |
| **JWKS endpoint unreachable** | Cached keys remain valid; on `kid` miss |
| | with stale cache, reject with `AuthException("signature", "key |
| | unavailable")`. |
| **JWKS returns invalid JSON** | Treat as key fetch failure; fall back to |
| | cached keys; reject on `kid` miss. |
| **Expired token** | `AuthException("expired", "token expired")`. |
| **Invalid signature** | `AuthException("signature", ...)`. |
| **Missing `kid` on RS256 token** | `AuthException("signature", "missing |
| | kid")`. |
| **`alg: none`** | `AuthException("signature", "algorithm none not |
| | allowed")`. |
| **Rate-limit exceeded** | `AuthException("signature", "key refresh |
| | rate-limited")`. |

Cancellation tokens are not part of the `AuthFilter` contract; verification
is synchronous and fast (local key lookup or bounded JWKS fetch).

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
- **Graceful overlap:** token signed by retiring key still verifies after
  new key is published; old key dropped after grace window.
- **Make-before-break:** no period where valid tokens from both old and new
  keys fail simultaneously.
- **Concurrent verification:** multiple threads verifying tokens against
  the same `JwtAuthFilter` instance; no key cache corruption.

### 11.3 Architectural enforcement

- `CorePurityTest` must continue to pass for every Tier 0 module, proving
  that no Spring AI or other framework types leak into `engine-core`.
- `apex-auth` remains Tier 2; jjwt remains a Tier 2 dependency only.
- `AuthFilter` signature unchanged — no breaking change.

### 11.4 Coverage targets

- All public `AuthFilter.authenticate` code paths exercised (HS256
  success/failure, RS256 success/failure, `none` rejection, expired,
  missing claims).
- JWKS cache hit/miss/refresh paths exercised.
- Rate-limit path exercised.
- Algorithm allowlist rejection paths exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. `apex-auth` builds under `mvn -B -f project/apex/pom.xml verify`.
2. `AuthFilter.authenticate(String)` signature is unchanged.
3. HS256 tokens verify with the shared secret (regression).
4. RS256 tokens verify against a JWKS-published public key selected by
   `kid`.
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

- **Feature flag / configuration:** RS256 verification is activated only
  when a `JwksProvider` is supplied to `JwtAuthFilter`. HS256-only
  operation is preserved by constructing `JwtAuthFilter` with only the
  shared secret.
- **Rollback:** If RS256 support causes issues, revert to constructing
  `JwtAuthFilter` with only the HS256 secret. No Tier 0 code changes
  required; `AuthFilter` signature unchanged.
- **Data migration:** Not applicable — JWTs are stateless; no stored
  state to migrate.

## 14. Out of Scope (Explicit)

The following items are deliberately excluded from this build spec and
must not be implemented under its authority:

- JWKS endpoint hosting / key publishing (issuer-side concern, not
  verifier).
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

**Decision governance:** Q4/Q5 (RS256 default, JWKS + kid,
make-before-break rotation, 90-day cadence, short-lived tokens,
algorithm allowlist) are closed and linked above. These decisions
constrain this spec as stated in each decision note's "Constraint on
future specs" section.
