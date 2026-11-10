# Spike A2 — Asymmetric JWT

**Status:** Finding  
**Date:** 2026-11-10  
**Outcome:** Promote to spec  
**Owner:** Engineering team

## Question

How should RS256 (or ES256) verification be added without disturbing the existing HS256 path?

## Current state

`apex-auth` currently implements JWT verification with HS256 only (symmetric shared secret) via jjwt. There is a single `TokenVerifier` port contract. The audit chain (`apex-audit`) remains HMAC-SHA256 and is unaffected by JWT algorithm choice.

## Options compared

| Option | Description | Coexists with HS256 | Key rotation | kid selection | Complexity |
|--------|-------------|---------------------|--------------|---------------|------------|
| **A2a. Dual-algorithm verifier (RS256 + HS256)** | One verifier inspects the JWT `alg` header; HS256 validates with shared secret, RS256 validates against a JWKS-published public key. `kid` selects the key. | Yes | Via JWKS refresh | Yes (JWKS `kid`) | Medium |
| **A2b. RS256-only, replace HS256** | Drop HS256 entirely; all tokens verified against JWKS public key. | No | Via JWKS refresh | Yes | Low |
| **A2c. Per-algorithm adapter ports** | Separate `Hs256TokenVerifier` and `Rs256TokenVerifier` ports; caller chooses. | Yes (two contracts) | Per-port | Per-port | High (breaking port change) |

## Required answers

### JWKS retrieval and caching

Recommended: a background `JwksProvider` fetches the JWKS endpoint on a schedule (e.g. every 5–15 min) and caches the `kid` → public-key map in memory with a TTL. On a `kid` cache miss during verification, trigger an immediate refresh once, then fail closed if still missing. Cache is keyed by `kid` to support rotation without downtime.

### Key rotation

JWKS rotation is handled by cache TTL expiry + refresh-on-miss. Old keys remain in cache briefly to validate in-flight tokens signed with the prior key. No coordination with the signer is required — this is the standard JWT rotation model.

### Clock skew

Allow a small clock-skew tolerance (e.g. ±30–60s) when validating `exp`/`nbf`, configurable per deployment. Do not permit skew on `iat` (issued-at) beyond a small window to reject obviously-future tokens.

### kid selection

`kid` is mandatory for RS256 tokens; tokens without `kid` fail verification. `kid` maps directly to a JWKS entry. HS256 tokens may omit `kid` (single shared secret).

### Can both algorithms coexist behind the same port contract?

Yes — **A2a**. The `TokenVerifier` port's `verify(token)` method stays unchanged; internally it branches on `alg`. HS256 and RS256 tokens are both accepted. Callers are unaware of the algorithm. This avoids a breaking port change (unlike A2c).

## Recommendation

**Promote A2a (dual-algorithm verifier with JWKS + kid)**.

Rationale:
- Preserves the existing HS256 path for current callers (no breaking change).
- Adds RS256 for asymmetric verification (public key published via JWKS, no shared secret distribution).
- `kid` enables safe key rotation.
- Single port contract means no consumer changes.
- Rejected **A2b**: drops HS256, breaking existing callers; not acceptable without a migration window.
- Rejected **A2c**: splits the port contract, requiring every caller to choose an algorithm — coupling callers to crypto details they should not know.

## Open questions

- JWKS endpoint URL and refresh interval are deployment-specific — mark **UNSET — requires stakeholder input**.
- Clock-skew tolerance value — **UNSET — requires stakeholder input**.
- Whether HS256 should eventually be deprecated (set a deprecation sunset date) — defer to ADR-002.

## Related

- ADR-002: JWT algorithm policy (HS256 + RS256 coexistence, JWKS) (to be written, Phase B)
- `project/apex-platform/apex-auth/` — current HS256 `TokenVerifier` (Tier 2)
