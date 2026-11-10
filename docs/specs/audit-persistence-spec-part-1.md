# BUILD-SPEC: Durable Audit Persistence (AuditPort SQLite Adapter) — Part 1 of 2

**Source:** docs/specs/audit-persistence-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 1 of 2
**Cross-reference:** See `audit-persistence-spec-part-2.md` for sections 7-15.

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
    // AuditPort methods: append(String, Instant), append(String, Instant,
    //   String, String), all(), verifyChain()
    // AutoCloseable: close()
}
```

### Compatibility statement

- **Port contract:** unchanged. No method signatures on `AuditPort` are
  altered.
- **`AuditEntry` model:** unchanged in v1. Adding `tenantId` is deferred to
  the tenancy spec (ADR-006) and is explicitly **out of scope** for this
  build spec.
- **Chain format:** stable across versions. The canonical serialization for
  hashing remains `previousHash | id | action | at.toEpochMilli() |
  traceId | spanId`. The SQLite schema does not alter the hash pre-image.

## 5. Dependency Ceiling and Justification

| Dependency | Scope | Justification |
| :--- | :--- | :--- |
| `java.base` | Compile/runtime | Required baseline. |
| `jspecify` | Compile | Null-safety annotations per project standards. |
| `org.xerial:sqlite-jdbc:3.45.0.0` | Runtime | The approved persistence engine per ADR-001. License: |
| | | BSD-style (compatible with Apache 2.0 / MIT / BSD / ISC allowed |
| | | set). |
| `org.junit.jupiter:junit-jupiter` | Test | Test framework per project standard. |
| `org.assertj:assertj-core` | Test | Assertions per project standard. |
| `com.tngtech.archunit:archunit-junit5` | Test | Architectural enforcement per project standard. |

**No new dependencies required.** jjwt 0.12.6 already supports RS256 via
`Keys.publicKey(...)` and `Jwts.parser().verifyWith(publicKey)`. JWKS
fetching is implemented with `java.net.http.HttpClient` (Java 11+, in
`java.base`).

**Tier 0 purity:** `apex-auth` is Tier 2 — external libraries are allowed.
No Tier 0 module is modified.

## 6. Implementation Design

### 6.1 Storage engine

Embedded **SQLite** with **WAL mode** enabled and `synchronous=FULL`
(per Q1 decision: RPO = 0 requires fsync durability; `synchronous=NORMAL`
may roll back committed transactions after power loss). The database file
lives under a configurable path supplied at construction time. No external
database process is required.

### 6.2 Schema

A single append-only ledger table:

| Column | Type | Constraints | Notes |
| :--- | :--- | :--- | :--- |
| `sequence` | INTEGER | PRIMARY KEY, AUTOINCREMENT | Monotonically increasing; |
| | | | used for ordering and O(1) resume. |
| `id` | TEXT | NOT NULL, UNIQUE | The `AuditEntry.id`. |
| `action` | TEXT | NOT NULL | PII-redacted action string |
| | | | (redaction performed by port contract). |
| `at` | INTEGER | NOT NULL | Epoch millis of `AuditEntry.at`. |
| `previous_hash` | TEXT | NOT NULL | Empty string for genesis. |
| `hash` | TEXT | NOT NULL | HMAC-SHA256 of the canonical |
| | | | pre-image. |
| `trace_id` | TEXT | NOT NULL, default `''` | Empty string when absent. |
| `span_id` | TEXT | NOT NULL, default `''` | Empty string when absent. |
| `key_id` | TEXT | NOT NULL, default `''` | Reserved per Q3 decision; empty |
| | | | in v1, ready for key rotation. |

**Append-only enforcement:** Rows are inserted, never updated or deleted
through the adapter API. Database triggers (`BEFORE UPDATE`, `BEFORE
DELETE`) reject mutations at the storage layer as defense-in-depth, in
addition to the application-level contract.

### 6.3 Append path

1. Redact PII from the action string (already a port-level responsibility;
   the adapter persists the redacted form).
2. Within a single transaction:
   - Determine the `previousHash` by reading `MAX(sequence)` and its
     `hash` (O(1) via the primary-key index; genesis uses `""`).
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
