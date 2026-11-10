# ADR-002: JWT algorithm policy (HS256 + RS256 coexistence, JWKS)

**Status:** Accepted  
**Deciders:** Engineering team  
**Date:** 2026-11-10  
**Supersedes:** none  
**Depends on:** Spike A2 (docs/spikes/A2-asymmetric-jwt.md)

## Context

`apex-auth` currently verifies JWTs with HS256 only (symmetric shared secret). Enterprise deployments require asymmetric verification (RS256/ES256) so the verifying service holds only a public key and key distribution does not require sharing a secret. The existing HS256 path must continue to work for current callers without a breaking change.

## Decision

**Adopt a dual-algorithm verifier (A2a): RS256 coexists with HS256 behind a single `TokenVerifier` port contract.**

- The `TokenVerifier.verify(token)` method signature is unchanged; internally it branches on the JWT `alg` header.
- HS256 tokens are validated against the existing shared secret.
- RS256 tokens are validated against a JWKS-published public key, selected by `kid`.
- A background `JwksProvider` fetches the JWKS endpoint on a schedule (TTL-based cache) and refreshes on `kid` cache miss; old keys are retained briefly for in-flight token validation (key rotation without downtime).
- `kid` is mandatory for RS256 tokens; HS256 tokens may omit `kid` (single shared secret).
- Clock-skew tolerance (±30–60s) is applied to `exp`/`nbf`, configurable per deployment; `iat` has a small future window.
- Verification fails closed: unknown `alg`, missing `kid` for RS256, or unresolved JWKS key → rejection.

## Alternatives considered

| Option | Verdict | Reason |
|--------|---------|--------|
| Dual-algorithm verifier (A2a) | **Accepted** | Preserves HS256; adds RS256; single port contract; no caller changes. |
| RS256-only, drop HS256 (A2b) | Rejected | Breaks existing callers; requires a migration window not yet authorized. |
| Per-algorithm adapter ports (A2c) | Rejected | Splits the port contract; couples callers to crypto details. |

## Consequences

### Positive
- Backward compatible: HS256 callers unaffected.
- Asymmetric verification available: public key via JWKS, no shared-secret distribution.
- Safe key rotation via JWKS cache + TTL.

### Negative
- `alg` header branching adds a small amount of verifier complexity.
- JWKS endpoint URL, refresh interval, and clock-skew tolerance are deployment-specific (UNSET — requires stakeholder input).

## Related

- docs/spikes/A2-asymmetric-jwt.md — the spike finding behind this decision
- `project/apex-platform/apex-auth/` — current HS256 `TokenVerifier` implementation (Tier 2)
