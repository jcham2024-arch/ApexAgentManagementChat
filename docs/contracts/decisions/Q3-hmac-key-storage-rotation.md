# Decision: Q3 — HMAC Key Storage and Rotation

## Question
HMAC-SHA256 key storage and rotation for audit chain.

## Chosen value
- **Key location:** Outside the audit volume, always. Sourced from a secret
  manager, KMS, or HSM. Never colocated with the audit database file.
- **Genesis rule:** The first entry in a chain uses `prev_hmac` of 64 zero
  bytes (0x00 × 32). Genesis is used exactly once per chain, never after
  rotation.
- **Rotation behavior:** Rotation never resets the chain. The next entry
  after rotation uses the last `hmac` from the prior key period as its
  `prev_hmac`.
- **Key identity:** Every record carries a `key_id` identifying which key
  signed it. The `key_id` column is reserved in the v1 schema (additive,
  non-breaking) for future use.
- **Rotation marker:** The rotation event is recorded in the chain, sealed
  with the old key that was in effect at the moment of rotation.
- **Retired keys:** Retained for verification. A retired key that is lost
  means every record it sealed fails verification.
- **Rotation frequency:** 90 days for symmetric HMAC keys, consistent with
  compliance practice. NIST SP 800-57 sets a maximum cryptoperiod of ≤2
  years for symmetric authentication keys; 90 days is within that ceiling.

## Approver
Security team (single 30-minute decision session).

## Evidence
Industry canonical operator guide for HMAC-chained audit logs:
- "the very first event in a chain uses a genesis prev_hmac of 64 zeros"
- "daily rotation never resets the chain — the next file's first entry uses
  the last hmac from yesterday"
- "keep the key off the audit volume" so that an attacker who can write the
  log cannot also read or rotate the signing key
NIST SP 800-57 Part 1: maximum cryptoperiod of ≤2 years for symmetric
authentication keys. Industry convergence on ≤90 days for symmetric HMAC
signing keys, limiting blast radius of key compromise across audit history.

## Rationale
The chain is keyed by the secret used to generate every HMAC already on
disk. Swapping the key invalidates every HMAC written before the swap
unless the verifier can select the correct key per record. This is why
`key_id` is mandatory from day one: without it, rotation either breaks
historical verification or forces a chain reset. A chain reset is not the
industry default; extending the chain across key periods with per-record
`key_id` is. Key location outside the audit volume addresses the threat of
an attacker with write access to the audit volume who could otherwise read
or rotate the signing key. 90 days balances the NIST ceiling (≤2 years)
with common compliance expectations (≤90 days), limiting key exposure
while remaining operationally feasible.

## Constraint on future specs
- Key storage MUST remain outside the audit volume. Colocating the HMAC key
  with the audit database file is forbidden.
- Rotation MUST NOT reset the chain. Future specs implementing key rotation
  MUST extend the chain across key periods using per-record `key_id`.
- `key_id` column MUST remain reserved in the audit ledger schema; removing
  it or repurposing it is forbidden.
- Retired keys MUST be retained for verification. Key deletion that would
  cause historical verification failure is forbidden without a documented
  chain-reset cutover (separate ADR required).
- Rotation frequency MAY be adjusted within the NIST ceiling (≤2 years)
  but any change requires security team re-approval.

## Link
- Governing ADR: `docs/adr/ADR-001-audit-persistence.md`
- Build spec governance: `docs/specs/audit-persistence-spec.md` (Q3 link)
- Security impact: `docs/contracts/nfr.md` §1 (HMAC key handling)
