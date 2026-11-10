# Decision: Q4/Q5 — Asymmetric JWT / RS256 and JWKS

## Question
Q4: JWKS endpoint URL and refresh interval.
Q5: Clock-skew tolerance window (`exp`/`nbf`).

## Chosen value
**Signing algorithm:** RS256 as the default for issuer-signed tokens; HS256 retained only for backward compatibility behind the same `TokenVerifier` port.
**Key distribution:** Public keys exposed at a JWKS endpoint (`/.well-known/jwks.json`); verifiers fetch via a remote JWKS client with an in-memory cache.
**Key selection:** Every token carries a `kid` header; verifier resolves the key by `kid`.
**Cache behavior:** Standard TTL of 24 hours; lazy one-time refresh on `kid` mismatch; rate-limit on-demand JWKS fetches (once per 5 minutes) to prevent DoS via random `kid` values.
**Graceful overlap:** Verifier accepts any valid key currently present in the JWKS set; rotation is make-before-break — new key published before old key retired.
**Rotation cadence:** 90-day signing key rotation, per NIST IR 8587 finalized guidance (one-hour token lifetime guidance, 90-day signing key rotation).
**Algorithm allowlist:** Explicit allowlist of expected algorithms; reject `none`; reject algorithm substitution attempts.
**Token lifetime:** Short-lived access tokens (5–15 minutes) with refresh-token rotation, per industry-standard JWT practice.

## Approver
Security + platform identity (30-minute decision session).

## Evidence
Industry consensus pattern for JWT key management:
- JWKS exposure at a well-known path, with public keys only and no HS* key material ever exposed.
- `kid`-based verification with grace windows: rotation adds a new key, moves the previous active key into a bounded `previous` set with a `retirementAt` timestamp, and drops it after the grace window.
- Lazy refresh on `kid` mismatch, rate-limited to prevent DoS, with graceful overlap so that in-flight tokens signed by the retiring key are still accepted.
- NIST IR 8587, finalized September 2026, explicitly sets 90-day signing key rotation alongside one-hour token guidance.

## Rationale
JWT key management has the most standardized industry consensus of any open question. A verifier that only knows one key will fail every token the moment the issuer rotates. Make-before-break rotation with a grace window prevents rotation from becoming an outage. HS256 remains supported behind the same port for backward compatibility; the port contract is additive, not breaking. The algorithm allowlist and short token lifetime limit blast radius if a key is compromised.

## Constraint on future specs
- RS256 is the default signing algorithm for issuer-signed tokens; HS256 is backward-compat only.
- The `TokenVerifier` port contract is additive, not breaking — no existing method signature changes.
- No HS* key material may be exposed in the JWKS document; only public keys belong there.
- JWKS cache TTL must not be measured in days while rotating keys on a 90-day cadence; the lazy-refresh path must be tested.
- Rotation must never be a key swap without a grace window; in-flight tokens must continue to validate.
- Algorithm allowlist must explicitly reject `none` and algorithm substitution attempts.

## Link
- Governing ADR: `docs/adr/ADR-002-jwt-algorithm-policy.md`
- Build spec: asymmetric JWT build spec (to be authored; readiness gate cleared)
