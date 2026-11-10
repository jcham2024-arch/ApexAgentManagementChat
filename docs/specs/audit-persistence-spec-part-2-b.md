# BUILD-SPEC: Durable Audit Persistence (AuditPort SQLite Adapter) — Part 2-b of 3

**Source:** docs/specs/audit-persistence-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 2-b of 3
**Cross-reference:** See `audit-persistence-spec-part-2-a.md` for sections 7-11.2.
**Cross-reference:** See `audit-persistence-spec-part-1.md` for sections 1-6.

## 11.3 Architectural enforcement

- `CorePurityTest` must continue to pass for every Tier 0 module,
  proving that no SQLite, JDBC, SQL, or file I/O type has leaked into
  `engine-core` or any other Tier 0 module[reference:11].
- The module's own `pom.xml` must not introduce any dependency that
  violates the Tier 1 dependency policy.

## 11.4 Coverage targets

- All public `AuditPort` methods exercised.
- Tamper-detection paths exercised (modification, deletion, reordering).
- Restart/resume path exercised.
- Concurrent append path exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. A new Tier 1 module exists at the specified location and builds under
   `mvn -B -f project/apex/pom.xml verify`.
2. The module implements `AuditPort` and is wired as a drop-in
   replacement for `InMemoryAuditLog` without changing any Tier 0 port
   signature or `AuditEntry` field.
3. The SQLite ledger table enforces append-only semantics at the
   storage layer.
4. An entry appended before a restart is present and chain-valid after
   restart.
5. `verifyChain()` returns `false` after any row is modified, deleted,
   or reordered, and `true` otherwise.
6. Concurrent appenders do not corrupt the chain or lose entries.
7. The canonical hash pre-image and HMAC algorithm are unchanged;
   hashes for identical inputs match the existing implementation.
8. No Tier 0 module gains a new dependency; `CorePurityTest` passes.
9. No `tenantId` field is added to `AuditEntry` in this spec.
10. The module `pom.xml` records the SQLite driver version and license
    compatibility.
11. Documentation is updated: `docs/contracts/capability-matrix.md`
    marks durable audit persistence as **implemented**, and the README
    module map includes the new adapter.
12. Q1–Q3 decision notes are linked from this spec's governance section
    (Section 15).

## 13. Rollback / Disable Strategy

- **Feature flag / configuration:** The adapter is instantiated only
  when a durable database path is configured. The existing
  `InMemoryAuditLog` remains available as a fallback for environments
  that cannot provide a writable filesystem.
- **Rollback:** If the SQLite adapter fails in production, the
  deployment can revert to the in-memory implementation (with the
  explicit understanding that restart durability is lost). No Tier 0
  code changes are required to switch implementations.
- **Data migration:** Not applicable for v1; the SQLite database is
  created fresh. Migrating existing in-memory chains into SQLite is
  out of scope.
- **Future upgrade path:** The deferred Postgres/MySQL ledger (A1c)
  can be introduced as a separate Tier 1 adapter behind the same
  `AuditPort` contract, without altering this module or Tier 0.

## 14. Out of Scope (Explicit)

The following items are deliberately excluded from this build spec and
must not be implemented under its authority:

- Adding `tenantId` to `AuditEntry` (governed by ADR-006; blocked on
  Q13).
- HMAC key rotation, key storage, or secrets management (Q3
  non-blocking; future work; `key_id` column reserved for future use).
- External relational database support (Postgres/MySQL) — deferred
  upgrade path per ADR-001.
- Changes to the `AuditPort` method signatures.
- Any modification to Tier 0 modules.
- File-based segment rotation (A1a was rejected).
- Performance tuning for specific latency/throughput targets (Q1–Q2 are
  non-blocking; SQLite defaults are acceptable for v1; benchmarks
  recorded in `docs/contracts/decisions/q2-benchmark-results.md`).

## 15. Governance References

| Artifact | Path |
| :--- | :--- |
| Spike finding | `docs/spikes/A1-audit-persistence.md` |
| Governing ADR | `docs/adr/ADR-001-audit-persistence.md` |
| Port contract inventory | `docs/contracts/ports.md` (AuditPort row, |
| | additive) |
| Capability matrix | `docs/contracts/capability-matrix.md` |
| | (Durable audit persistence) |
| NFR capture | `docs/contracts/nfr.md` §1 |
| Open questions | `docs/contracts/open-questions.md` |
| | (Q1–Q3 closed) |
| Spec readiness checklist | `docs/specs/README.md` (READY) |
| Q1 decision: RPO/RTO | `docs/contracts/decisions/Q1-audit-rpo-rto.md` |
| Q2 decision: latency/throughput | `docs/contracts/decisions/`
| | Q2-audit-latency-throughput.md |
| Q3 decision: key storage/rotation | `docs/contracts/decisions/`
| | Q3-hmac-key-storage-rotation.md |
| Q2 benchmark evidence | `docs/contracts/decisions/q2-benchmark-results.md` |

**Decision governance:** Q1 (RPO = 0, RTO = 4 h), Q2 (p99 ≤ 10 ms,
≥ 100 appends/sec, synchronous durability), and Q3 (key outside audit
volume, genesis 64 zeros, rotation never resets chain, `key_id` per
record, 90-day baseline) are closed and linked above. These decisions
constrain this spec and all future audit-related specs as stated in each
decision note's "Constraint on future specs" section.
