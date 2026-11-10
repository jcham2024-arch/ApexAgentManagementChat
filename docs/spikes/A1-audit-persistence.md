# Spike A1 — Audit Persistence

**Status:** Finding  
**Date:** 2026-11-10  
**Outcome:** Promote to spec (with caveats)  
**Owner:** Engineering team

## Question

What durable, append-only store satisfies tamper-evidence, monotonic timestamp enforcement, and multi-writer safety for the `AuditPort` hash chain, replacing the current in-memory `InMemoryAuditLog`?

## Options compared

| Option | Description | Durability | Tamper-evidence | Multi-writer | Complexity |
|--------|-------------|------------|-----------------|--------------|------------|
| **A1a. Append-only file + fsync + segment rotation** | Each entry appended as a line to a segment file; fsync after each write; segments rotated on size/time; chain hash carried in a segment header/footer. | High (fsync) | Strong (hash chain within + across segments) | Weak (file locking fragile across processes) | Medium |
| **A1b. Embedded SQL (H2/SQLite) with write-once constraint** | Ledger table with append-only semantics enforced by a write-once trigger/constraint; entries keyed by monotonically increasing sequence. | High (WAL + fsync) | Strong (chain hash column verified on read) | Strong (DB row-level locking + serializable) | Medium |
| **A1c. Relational store with ledger table** | External Postgres/MySQL ledger table; append-only enforced by application + DB constraints; sequence from DB sequence object. | Highest (managed) | Strong (chain hash column) | Strongest (native concurrency) | High (operational dependency) |

## Required answers

### How is the HMAC chain preserved across segment boundaries?

For **A1a**, each segment file stores its first entry's `previousHash` (inherited from the last entry of the prior segment) and its last entry's `hash`. Verification walks segments in order, carrying `previousHash` from the end of segment N into the start of segment N+1. A segment boundary is itself a hash commitment point, so truncation or reordering of segments is detectable.

For **A1b/A1c**, there are no segments — the chain lives in a single append-only table keyed by a monotonically increasing `sequence` column. The `previousHash` of entry N+1 references the `hash` of entry N; verification orders by `sequence` and recomputes.

### How is the previous-entry commitment carried across restarts?

- **A1a:** On restart, read the last segment file's final entry hash; that becomes the `previousHash` for the next append. Persist a small "tail pointer" (last sequence + last hash) in a sidecar file updated atomically with fsync so recovery does not require scanning all segments.
- **A1b/A1c:** On restart, query `MAX(sequence)` and its `hash` from the ledger table; that becomes the `previousHash` for the next insert. No scan required; O(1) via index on `sequence`.

### What happens on partial write?

- **A1a:** A partial line is detectable on read (delimiter/JSON parse failure or length mismatch). On recovery, truncate the partial tail and resume from the last valid entry's hash. fsync after each complete write minimizes the window.
- **A1b/A1c:** A partial insert is atomic at the DB row level (transaction rollback). Either the full entry commits or none of it does; no partial state is ever visible. This is the strongest guarantee.

## Recommendation

**Promote A1b (embedded SQL, SQLite) as the recommended option**, with A1c (external relational) as the acceptable upgrade path for multi-node deployments.

Rationale:
- SQLite provides row-level atomicity and WAL durability (fsync) without an operational dependency — matches the air-gapped, single-node SBC target.
- The write-once constraint + sequence keying enforces append-only and monotonic ordering natively.
- Chain verification is a single ordered scan with hash recomputation — no segment-boundary bookkeeping.
- Rejected **A1a**: file locking is fragile for multi-writer safety and segment-boundary hash management adds complexity without compensating benefit.
- Rejected **A1c** for v1: introduces an operational dependency (Postgres/MySQL) that conflicts with the air-gapped, zero-infra Tier 0 deployment story; keep as the documented multi-node upgrade path.

## Caveat / open question

Tier 0 (engine-core) must not depend on `java.io`/`java.nio.file`/`java.sql`/SQLite per AGENT_RULES §2. Therefore the SQLite implementation **must live in a Tier 1 adapter** (`engine-store-sqlite` or a new audit-persistence adapter), with `AuditPort` remaining the Tier 0 contract. This requires an ADR (ADR-001) confirming the Tier 0 / Tier 1 split and that Tier 0 never sees the persistence technology.

## Related

- ADR-001: Audit persistence model and durability guarantees (to be written, Phase B)
- `project/apex-platform/apex-audit/` — current in-memory `HashChain` + `InMemoryAuditLog` (Tier 2)
