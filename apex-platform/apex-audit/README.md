# apex-audit

**Tier:** 2 · **Purpose:** Chained audit log with PII redaction.

## Allowed dependencies
any (Tier 2); currently java.base + javax.crypto (from JRE) only

## Ports consumed
none

## Ports implemented
AuditPort (via InMemoryAuditLog)

## Chain format
Each entry commits to its predecessor via HMAC-SHA256 over
`previousHash|id|action|atMillis`. Genesis entries carry
`previousHash = ""`. Any alteration to any entry breaks the chain from
that point forward. Verification walks the chain from genesis and
recomputes every hash.

## PII redaction
Applied before hashing. Fixed-string replacement markers:
- `[EMAIL]`, `[PHONE]`, `[SSN]`, `[AWS_KEY]`, `[GITHUB_TOKEN]`,
  `[API_KEY]`, `[PEM_BLOCK]`

Identical redacted inputs produce identical hashes.

## Not implemented in S3.3
- File-backed persistence (S6)
- Monotonic timestamp enforcement (S6)
- Multi-writer coordination (S6)

## Tests
HashChainTest · PiiRedactorTest · InMemoryAuditLogTest
