# ADR-001: Audit persistence model and durability guarantees

**Status:** Accepted  
**Deciders:** Engineering team  
**Date:** 2026-11-10  
**Supersedes:** none  
**Depends on:** Spike A1 (docs/spikes/A1-audit-persistence.md)

## Context

The `AuditPort` hash chain is currently implemented only in memory (`InMemoryAuditLog`, Tier 2, `apex-audit`). There is no durable, append-only persistence. On restart the chain is lost, which violates the enterprise requirement that every state mutation be tamper-evident and reconstructible.

Tier 0 (`engine-core`) may not use `java.io`, `java.nio.file`, `java.sql`, or any third-party infrastructure dependency (AGENT_RULES §2). Therefore audit persistence cannot be implemented in Tier 0; it must live in a Tier 1 adapter behind the `AuditPort` contract.

## Decision

**Adopt an embedded SQLite ledger table (A1b) as the audit persistence model**, implemented in a Tier 1 adapter (e.g. `engine-store-sqlite` or a dedicated audit-persistence adapter).

- The ledger table is append-only: rows keyed by a monotonically increasing `sequence`, each row storing `hash`, `previousHash`, `action` (PII-redacted), `at`, `tenantId`, `traceId`, `spanId`.
- Append is a single transactional row insert with WAL + fsync durability.
- `verifyChain()` orders by `sequence` and recomputes each `hash` from `previousHash | id | action | at | traceId | spanId`, failing fast on mismatch.
- On restart, `MAX(sequence)` + its `hash` is read (O(1) via index) to resume the chain; no full-table scan required.
- Partial writes cannot be visible: SQLite row-level atomicity guarantees each insert commits whole or not at all.
- Tier 0 depends only on the `AuditPort` interface (`java.base` + `jspecify`). Tier 0 never sees SQLite, SQL, or file I/O.

## Alternatives considered

| Option | Verdict | Reason |
|--------|---------|--------|
| Append-only file + fsync + segment rotation (A1a) | Rejected | File locking fragile for multi-writer; segment-boundary hash bookkeeping adds complexity without benefit. |
| Embedded SQLite ledger (A1b) | **Accepted** | Row-level atomicity, WAL durability, O(1) chain resume, no operational dependency. |
| External relational ledger (Postgres/MySQL) (A1c) | Deferred | Strong multi-node story but introduces an operational dependency; keep as the documented multi-node upgrade path. |

## Consequences

### Positive
- Tamper-evidence survives restarts; chain is reconstructible from durable rows.
- Multi-writer safety via SQLite locking.
- Tier 0 purity preserved — no infra dependency in core.

### Negative
- Audit persistence is SQLite-specific for v1; migrating to Postgres/MySQL later requires a new Tier 1 adapter.
- `AuditEntry` model may need a `tenantId` field (additive, non-breaking) to scope entries per tenant — see ADR-006.

## Related

- docs/spikes/A1-audit-persistence.md — the spike finding behind this decision
- `project/apex-core/engine-core/src/main/java/com/apex/engine/core/port/AuditPort.java` — Tier 0 contract (unchanged)
- `project/apex-platform/apex-audit/` — current in-memory `HashChain` + `InMemoryAuditLog` (Tier 2, remains for non-persisted use)
