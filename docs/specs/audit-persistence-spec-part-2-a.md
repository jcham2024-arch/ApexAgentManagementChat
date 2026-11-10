# BUILD-SPEC: Durable Audit Persistence (AuditPort SQLite Adapter) — Part 2-a of 3

**Source:** docs/specs/audit-persistence-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 2-a of 3
**Cross-reference:** See `audit-persistence-spec-part-2-b.md` for sections 11.3-15.
**Cross-reference:** See `audit-persistence-spec-part-1.md` for sections 1-6.

## 7. Determinism Impact

**No impact on Tier 0 determinism.** The adapter does not influence plan
generation, tie-breaking, or the order of audit emission from the caller's
perspective. `all()` must return entries in `sequence` order, which is the
append order. `verifyChain()` is deterministic: identical durable rows
always produce the same verification result.

**Invariant:** The canonical hash pre-image and the HMAC algorithm remain
unchanged from the in-memory implementation. The durable adapter produces
byte-identical hashes for the same inputs, preserving cross-implementation
chain compatibility.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **HMAC key handling** | The key is supplied to the adapter at construction |
| | time. It must be at least 32 bytes (existing requirement). Key |
| | storage, rotation, and retrieval are **out of scope** for this |
| | spec (open question Q3 is non-blocking; rotation is future |
| | work). |
| **PII** | The port contract mandates redaction before hashing. The |
| | adapter persists only redacted actions. No additional PII |
| | handling is introduced. |
| **Tenant scoping** | `tenantId` is **not** added in this spec. Tenant |
| | isolation on audit entries is governed by ADR-006 and remains |
| | blocked on Q13. The schema should be designed to accept an |
| | additive `tenantId` column later without breaking existing |
| | rows. |
| **Storage access** | The SQLite file path is configurable; |
| | filesystem permissions are the responsibility of the |
| | deployment environment. The adapter must not log the HMAC key |
| | or raw pre-images. |

## 9. Audit Impact

The adapter **is** the audit persistence layer. It records every
`AuditEntry` it receives without alteration to the public fields,
preserving the chain commitment semantics. The following must be
guaranteed:

- **Durability:** A committed entry survives process restart.
- **Tamper-evidence:** Any post-hoc modification to a row causes
  `verifyChain()` to fail.
- **Monotonic ordering:** `sequence` strictly increases; `all()`
  respects this order.
- **No silent loss:** A failed append must throw an exception or
  otherwise fail loudly; it must never silently drop an entry.
- **Chain resumption:** After restart, the next append correctly
  commits to the last durable entry's hash.
