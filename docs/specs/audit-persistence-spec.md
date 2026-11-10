# BUILD-SPEC: Durable Audit Persistence (AuditPort SQLite Adapter)

## 1. Module Identity

| Field | Value |
| :--- | :--- |
| **Module name** | `audit-persistence-sqlite` |
| **Repository location** | `project/apex-platform/apex-audit-persistence-sqlite/` |
| **Parent aggregator** | `project/apex-platform/pom.xml` |
| **Tier** | **Tier 1** (adapter) |
| **Governed by** | ADR-001 (Audit persistence model) |
| **Spike input** | `docs/spikes/A1-audit-persistence.md` |
| **Spec readiness** | **READY** per `docs/specs/README.md` (D8.1–D8.7 all satisfied) |

## 2. Purpose

Replace the in-memory `InMemoryAuditLog` with a durable, append-only SQLite
ledger that preserves the existing HMAC-SHA256 hash chain across process
restarts, enforces monotonic sequencing, and provides O(1) chain resumption
without a full-table scan. The adapter satisfies the enterprise requirement
that every state mutation is tamper-evident and reconstructible after restart,
while keeping Tier 0 completely free of SQLite, SQL, or file I/O dependencies.

## 3. Ports Consumed and Provided

### Consumed (Tier 0 contract)

- **`AuditPort`** — `com.apex.engine.core.port.AuditPort`
  - `AuditEntry append(String action, Instant at, String traceId, String spanId)`
  - `List<AuditEntry> all()`
  - `boolean verifyChain()`

- **`AuditEntry`** — `com.apex.engine.core.port.model.AuditEntry`
  - Fields: `id`, `action`, `at`, `previousHash`, `hash`, `traceId`, `spanId`.
  - Genesis entries carry `previousHash = ""` (empty string, not null).

### Provided (Tier 1 implementation)

- A concrete `AuditPort` implementation backed by an embedded SQLite database:
  `SqliteAuditLog` (`com.apex.platform.audit.persistence.sqlite.SqliteAuditLog`).
- No new Tier 0 ports are introduced. The port contract is **additive**: the
  durable implementation is a new adapter behind an existing interface.

## 4. Public API Surface and Compatibility

### Java API

The module exposes a single public class implementing `AuditPort`:

```java
public final class SqliteAuditLog implements AuditPort, AutoCloseable {
    public SqliteAuditLog(Path databasePath, byte[] hmacKey);
    // AuditPort methods: append(String, Instant), append(String, Instant, String, String),
    // all(), verifyChain()
    // AutoCloseable: close()
}
```

### Compatibility statement

- **Port contract:** unchanged. No method signatures on `AuditPort` are altered.
- **`AuditEntry` model:** unchanged in v1. Adding `tenantId` is deferred to the
  tenancy spec (ADR-006) and is explicitly **out of scope** for this build spec.
- **Chain format:** stable across versions. The canonical serialization for
  hashing remains `previousHash | id | action | at.toEpochMilli() | traceId |
  spanId`. The SQLite schema does not alter the hash pre-image.

## 5. Dependency Ceiling and Justification

| Dependency | Scope | Justification |
| :--- | :--- | :--- |
| `java.base` | Compile/runtime | Required baseline. |
| `jspecify` | Compile | Null-safety annotations per project standards. |
| `org.xerial:sqlite-jdbc:3.45.0.0` | Runtime | The approved persistence engine per ADR-001. License: BSD-style |
| | | (compatible with Apache 2.0 / MIT / BSD / ISC allowed set). |
| `org.junit.jupiter:junit-jupiter` | Test | Test framework per project standard. |
| `org.assertj:assertj-core` | Test | Assertions per project standard. |

**Tier 0 purity:** No SQLite, SQL, file I/O, or JDBC type may appear in
`engine-core` or any other Tier 0 module. The Tier 0 dependency set remains
`java.base` + `jspecify` only. A `CorePurityTest` must continue to pass for
all Tier 0 modules.

## 6. Persistence Design

### 6.1 Storage engine

Embedded **SQLite** with **WAL mode** enabled and `synchronous=FULL`
(per Q1 decision: RPO = 0 requires fsync durability; `synchronous=NORMAL` may
roll back committed transactions after power loss). The database file lives
under a configurable path supplied at construction time. No external database
process is required.

### 6.2 Schema

A single append-only ledger table:

| Column | Type | Constraints | Notes |
| :--- | :--- | :--- | :--- |
| `sequence` | INTEGER | PRIMARY KEY, AUTOINCREMENT | Monotonically increasing; used for ordering and O(1) resume. |
| `id` | TEXT | NOT NULL, UNIQUE | The `AuditEntry.id`. |
| `action` | TEXT | NOT NULL | PII-redacted action string (redaction performed by port contract). |
| `at` | INTEGER | NOT NULL | Epoch millis of `AuditEntry.at`. |
| `previous_hash` | TEXT | NOT NULL | Empty string for genesis. |
| `hash` | TEXT | NOT NULL | HMAC-SHA256 of the canonical pre-image. |
| `trace_id` | TEXT | NOT NULL, default `''` | Empty string when absent. |
| `span_id` | TEXT | NOT NULL, default `''` | Empty string when absent. |
| `key_id` | TEXT | NOT NULL, default `''` | Reserved per Q3 decision; empty in v1, ready for key rotation. |

**Append-only enforcement:** Rows are inserted, never updated or deleted
through the adapter API. Database triggers (`BEFORE UPDATE`, `BEFORE DELETE`)
reject mutations at the storage layer as defense-in-depth, in addition to the
application-level contract.

### 6.3 Append path

1. Redact PII from the action string (port-level responsibility; the adapter
   persists the redacted form).
2. Within a single transaction:
   - Determine the `previousHash` by reading `MAX(sequence)` and its `hash`
     (O(1) via the primary-key index; genesis uses `""`).
   - Compute the HMAC-SHA256 hash using the supplied key.
   - Insert one row.
3. Commit with WAL durability. A partial insert is not visible: SQLite
   row-level atomicity guarantees whole-or-nothing visibility.

### 6.4 Restart recovery

On construction:

1. Query `MAX(sequence)` and the corresponding `hash`.
2. If no rows exist, the chain starts at genesis with `previousHash = ""`.
3. Otherwise, the next append uses the last row's hash as `previousHash`.
4. No full-table scan is required; the resume is O(1).

### 6.5 Verification (`verifyChain()`)

1. Read all rows ordered by `sequence` ascending.
2. For each row, recompute the HMAC-SHA256 from the canonical pre-image and
   compare to the stored `hash`.
3. Confirm that each row's `previous_hash` matches the predecessor's `hash`
   (genesis expects `""`).
4. Return `false` on the first mismatch, covering tampering, reordering,
   deletion, and insertion.

## 7. Determinism Impact

**No impact on Tier 0 determinism.** The adapter does not influence plan
generation, tie-breaking, or the order of audit emission from the caller's
perspective. `all()` must return entries in `sequence` order, which is the
append order. `verifyChain()` is deterministic: identical durable rows always
produce the same verification result.

**Invariant:** The canonical hash pre-image and the HMAC algorithm remain
unchanged from the in-memory implementation. The durable adapter produces
byte-identical hashes for the same inputs, preserving cross-implementation
chain compatibility.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **HMAC key handling** | The key is supplied to the adapter at construction time. It must |
| | be at least 32 bytes (existing requirement). Key storage, rotation, and |
| | retrieval are governed by Q3 decision: key outside the audit volume, |
| | sourced from secret manager/KMS/HSM; never colocated with the database. |
| **PII** | The port contract mandates redaction before hashing. The adapter |
| | persists only redacted actions. No additional PII handling introduced. |
| **Tenant scoping** | `tenantId` is not added in this spec (governed by ADR-006, |
| | blocked on Q13). The `key_id` column is reserved in v1 schema for future |
| | extensibility without breaking existing rows. |
| **Storage access** | The SQLite file path is configurable; filesystem permissions |
| | are the deployment environment's responsibility. The adapter must not |
| | log the HMAC key or raw pre-images. |

## 9. Audit Impact

The adapter **is** the audit persistence layer. It records every
`AuditEntry` it receives without alteration to the public fields, preserving
the chain commitment semantics. Guarantees:

- **Durability:** A committed entry survives process restart.
- **Tamper-evidence:** Any post-hoc modification to a row causes
  `verifyChain()` to fail.
- **Monotonic ordering:** `sequence` strictly increases; `all()` respects
  this order.
- **No silent loss:** A failed append throws an exception; it never silently
  drops an entry.
- **Chain resumption:** After restart, the next append correctly commits to
  the last durable entry's hash.

## 10. Failure, Cancellation, and Timeout Semantics

| Scenario | Required Behavior |
| :--- | :--- |
| **Database file unavailable / locked** | Construction fails fast with a clear |
| | exception. Appends fail with a runtime exception; callers must not assume |
| | success. |
| **Disk full / I/O error during append** | Transaction rolls back; no partial row |
| | is visible. Exception is propagated. |
| **Partial write** | SQLite atomicity prevents partial visibility. On restart, the |
| | database is consistent up to the last committed transaction. |
| **Concurrent appenders** | SQLite locking serializes writes. The adapter |
| | does not use a shared mutable cache that could reorder entries. |
| **`verifyChain()` detects mismatch** | Returns `false`; no exception is |
| | required by the port contract. |
| **Cancellation** | The append operation is short-lived and transactional. |
| | Cancellation tokens are not part of the `AuditPort` contract and are not |
| | introduced in this spec. |

## 11. Test Plan

### 11.1 Unit tests

- Schema creation and migration (if a schema version table is used).
- Genesis append produces `previousHash = ""`.
- Sequential appends chain correctly (entry N's `previousHash` equals entry
  N−1's `hash`).
- `all()` returns entries in `sequence` order.
- `verifyChain()` returns `true` for an untampered chain.
- Redacted action is persisted; no raw PII is written.
- Key length validation (minimum 32 bytes) behaves consistently with the
  existing in-memory implementation.

### 11.2 Integration tests

- **Restart persistence:** Append entries, close the adapter, reopen the same
  database file, verify the chain resumes correctly and `verifyChain()` still
  passes.
- **O(1) resume:** Confirm restart does not perform a full-table scan.
- **Tamper detection:** Directly modify a row (or insert a rogue row with a
  broken hash) in the database outside the adapter, then confirm
  `verifyChain()` returns `false`.
- **Concurrent appends:** Multiple threads appending through a shared adapter
  instance against the same database file; verify no lost entries and a valid
  chain.
- **Partial-write simulation:** Simulate a failed transaction (injected SQL
  exception) and verify no partial row is visible after restart.
- **Genesis edge case:** Empty database, first append, restart, second append;
  chain remains valid.

### 11.3 Architectural enforcement

- `CorePurityTest` must continue to pass for every Tier 0 module, proving
  that no SQLite, JDBC, SQL, or file I/O type has leaked into `engine-core`
  or any other Tier 0 module.
- The module's own `pom.xml` must not introduce any dependency that violates
  the Tier 1 dependency policy.

### 11.4 Coverage targets

- All public `AuditPort` methods exercised.
- Tamper-detection paths exercised (modification, rogue row insertion).
- Restart/resume path exercised.
- Concurrent append path exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. A new Tier 1 module exists at the specified location and builds under
   `mvn -B -f project/apex/pom.xml verify`.
2. The module implements `AuditPort` and is wired as a drop-in replacement
   for `InMemoryAuditLog` without changing any Tier 0 port signature or
   `AuditEntry` field.
3. The SQLite ledger table enforces append-only semantics at the storage
   layer (UPDATE/DELETE triggers reject mutations).
4. An entry appended before a restart is present and chain-valid after
   restart.
5. `verifyChain()` returns `false` after any row is modified, deleted, or a
   rogue row is inserted, and `true` otherwise.
6. Concurrent appenders do not corrupt the chain or lose entries.
7. The canonical hash pre-image and HMAC algorithm are unchanged; hashes for
   identical inputs match the existing implementation.
8. No Tier 0 module gains a new dependency; `CorePurityTest` passes.
9. No `tenantId` field is added to `AuditEntry` in this spec.
10. The module `pom.xml` records the SQLite driver version and license
    compatibility.
11. Documentation is updated: `docs/contracts/capability-matrix.md` marks
    durable audit persistence as **implemented**, and the README module map
    includes the new adapter.
12. Q1–Q3 decision notes are linked from this spec's governance section
    (Section 15).

## 13. Rollback / Disable Strategy

- **Feature flag / configuration:** The adapter is instantiated only when a
  durable database path is configured. The existing `InMemoryAuditLog`
  remains available as a fallback for environments that cannot provide a
  writable filesystem.
- **Rollback:** If the SQLite adapter fails in production, the deployment can
  revert to the in-memory implementation (with the explicit understanding
  that restart durability is lost). No Tier 0 code changes are required to
  switch implementations.
- **Data migration:** Not applicable for v1; the SQLite database is created
  fresh. Migrating existing in-memory chains into SQLite is out of scope.
- **Future upgrade path:** The deferred Postgres/MySQL ledger (A1c) can be
  introduced as a separate Tier 1 adapter behind the same `AuditPort`
  contract, without altering this module or Tier 0.

## 14. Out of Scope (Explicit)

The following items are deliberately excluded from this build spec and must
not be implemented under its authority:

- Adding `tenantId` to `AuditEntry` (governed by ADR-006; blocked on Q13).
- HMAC key rotation, key storage, or secrets management (Q3 non-blocking;
  future work; `key_id` column reserved for future use).
- External relational database support (Postgres/MySQL) — deferred upgrade
  path per ADR-001.
- Changes to the `AuditPort` method signatures.
- Any modification to Tier 0 modules.
- File-based segment rotation (A1a was rejected).
- Performance tuning for specific latency/throughput targets (Q1–Q2 are
  non-blocking; SQLite defaults are acceptable for v1; benchmarks recorded
  in `docs/contracts/decisions/q2-benchmark-results.md`).

## 15. Governance References

| Artifact | Path |
| :--- | :--- |
| Spike finding | `docs/spikes/A1-audit-persistence.md` |
| Governing ADR | `docs/adr/ADR-001-audit-persistence.md` |
| Port contract inventory | `docs/contracts/ports.md` (AuditPort row, additive) |
| Capability matrix | `docs/contracts/capability-matrix.md` (Durable audit persistence) |
| NFR capture | `docs/contracts/nfr.md` §1 |
| Open questions | `docs/contracts/open-questions.md` (Q1–Q3 closed) |
| Spec readiness checklist | `docs/specs/README.md` (READY) |
| Q1 decision: RPO/RTO | `docs/contracts/decisions/Q1-audit-rpo-rto.md` |
| Q2 decision: latency/throughput | `docs/contracts/decisions/Q2-audit-latency-throughput.md` |
| Q3 decision: key storage/rotation | `docs/contracts/decisions/Q3-hmac-key-storage-rotation.md` |
| Q2 benchmark evidence | `docs/contracts/decisions/q2-benchmark-results.md` |

**Decision governance:** Q1 (RPO = 0, RTO = 4 h), Q2 (p99 ≤ 10 ms,
≥ 100 appends/sec, synchronous durability), and Q3 (key outside audit volume,
genesis 64 zeros, rotation never resets chain, `key_id` per record, 90-day
baseline) are closed and linked above. These decisions constrain this spec
and all future audit-related specs as stated in each decision note's
"Constraint on future specs" section.
