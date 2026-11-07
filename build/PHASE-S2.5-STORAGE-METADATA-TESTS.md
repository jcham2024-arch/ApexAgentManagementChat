# PHASE-S2.5-STORAGE-METADATA-TESTS.md — Session Package, Part 4 of 5

**Version:** 1.0 · **Sub-phase of S2** · **Part 4 of 5:** §4.4 and §5, metadata and tests.
Parts: SESSION · POM-EXCEPTION · SQLITE-PORT · METADATA-TESTS ·
CRITERIA, each `PHASE-S2.5-STORAGE-<part>.md` (`AGENT_RULES.md` §2.13).

---
### 4.4 Update `engine-store-sqlite` metadata

**`engine-store-sqlite/src/main/java/com/apex/engine/store/sqlite/package-info.java`** — replace content:

```java
/**
 * SQLite-backed StoragePort for step and session logs. Tier 1.
 * Implements: StoragePort. Consumes: java.sql, org.xerial:sqlite-jdbc.
 * Server and SBC deployment; Android uses Room (S9).
 */
@NullMarked
package com.apex.engine.store.sqlite;

import org.jspecify.annotations.NullMarked;
```

**`engine-store-sqlite/README.md`** — replace content:

```markdown
# engine-store-sqlite

**Tier:** 1 · **Purpose:** SQLite-backed StoragePort for step and
session logs.

## Allowed dependencies
java.base, java.sql, org.xerial:sqlite-jdbc, jspecify

## Ports consumed
none

## Ports implemented
StoragePort (via SqliteStoragePort)

## Design notes
- Each operation opens its own JDBC connection; WAL mode + 5s busy
  timeout applied on connect.
- Schema is minimal (five columns matching StepLogEntry). The richer
  step_log schema lands in S5.
- Duplicate step_id throws StorageException("duplicate").
- This adapter is for server and SBC. Android uses Room (S9) against
  the same StoragePort interface.

## Tests
SqliteStoragePortTest · SqliteStoragePortConcurrencyTest
```

## 5. Tests

Package `com.apex.engine.store.sqlite` (mirror main).

### 5.1 `SqliteStoragePortTest.java`

Use `@TempDir Path tempDir;` from JUnit 5. Instantiate the port against
`tempDir.resolve("test.db")` in `@BeforeEach`.

Test cases:

1. **appendAndQueryRoundTrip** — append one entry, query by session,
   assert exactly one match with equal fields.
2. **queryOrdersByStepIndex** — append three entries with indices 2,
   0, 1 in that insertion order; query; assert returned list is ordered
   0, 1, 2.
3. **queryFiltersBySession** — append one entry to session "a" and one
   to session "b"; query "a"; assert one result and it belongs to "a".
4. **queryEmptySessionReturnsEmpty** — query "never-seen"; assert
   empty list.
5. **duplicateStepIdThrows** — append the same entry twice; second
   call throws `StorageException` with category "duplicate".
6. **persistenceAcrossReopen** — append one entry; construct a new
   `SqliteStoragePort` against the same path; query; assert the entry
   is present.
7. **createdAtNotInSchema** — introspection test: query
   `PRAGMA table_info(step_log)` directly, assert the column set
   equals exactly `{step_id, session_id, step_index, step_type,
   duration_ms}`. (Prevents accidental schema drift.)
8. **walModeEnabled** — after construction, query
   `PRAGMA journal_mode` on a fresh connection; assert result is
   `"wal"`.

Each test class under 200 lines. If this file reaches 190 lines, split
test #7 and #8 into a second file
`SqliteStoragePortSchemaTest.java`.

### 5.2 `SqliteStoragePortConcurrencyTest.java`

Two concurrent writer threads to different sessions; single reader.
Tests:

1. **concurrentAppendsDifferentSessions** — 2 threads × 50 appends
   each (different session per thread); after both complete, assert 50
   entries for each session.
2. **concurrentAppendsSameSession** — 2 threads × 50 appends each, all
   to the same session with distinct step_indices; after completion,
   assert 100 entries total.
3. **queryDuringAppend** — one thread appends 100 entries; a second
   thread queries every 10 ms; assert queries never return more than
   100 entries and never throw. (Proves WAL doesn't block reads.)

Each test uses `ExecutorService` with fixed thread pool; shut down in
`@AfterEach`. Under 200 lines.
