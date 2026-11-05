# PHASE-S2.5-STORAGE-SESSION.md — Session Package, Part 1 of 5

**Version:** 1.0 · **Sub-phase of S2** · **Part 1 of 5:** reading protocol, context deltas, prerequisite, scope.
Parts: SESSION · POM-EXCEPTION · SQLITE-PORT · METADATA-TESTS ·
CRITERIA, each `PHASE-S2.5-STORAGE-<part>.md` (`AGENT_RULES.md` §2.13).

---
## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S2-KERNEL.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S2.5

Authoritative where they conflict with any prior file. Do not stop
over them.

### 1.1 Spec not required

Router §4 says S2.5 does not need the spec. Holds. The storage adapter
consumes `StoragePort` and `StepLogEntry` (both from S2.1). No Layer 1
types.

### 1.2 `sqlite-jdbc` **is** a Maven dependency

S2.0 §1.4 said "do not add a Maven dependency for SQLite." That note
was wrong: SQLite is not part of the JDK. `java.sql` is the JDBC API,
but there is no built-in driver. **This sub-phase adds
`org.xerial:sqlite-jdbc` to `engine-store-sqlite/pom.xml`.** It is
Apache 2.0, not on the Tier 1 Enforcer ban list, and its transitive
dependencies (slf4j-api, in some versions) are also not banned. If the
Enforcer fails on this dependency, stop and report — do not disable
the Enforcer.

### 1.3 Schema is minimal

`StepLogEntry` (from S2.1) has five fields:
`(stepId, sessionId, index, stepType, durationMs)`. The SQLite schema
mirrors exactly those five fields. **Do not add `created_at` or any
other column.** The richer `step_log` schema (thinking_json,
action_json, error_class, etc.) lands in **S5** when the flywheel needs
it, via an `ALTER TABLE` migration in that phase. Keep S2.5 minimal —
prove `StoragePort` works, nothing more.

### 1.4 No timestamps, no clock injection

Because §1.3 drops `created_at`, no clock is needed. Do not add a
`Clock` field. This keeps the adapter trivially deterministic.

### 1.5 One connection per operation

`SqliteStoragePort` opens a fresh JDBC connection for each method call.
SQLite with WAL mode handles concurrent writers safely at the file
level. **Do not** hold a long-lived connection, do not build a
connection pool, do not use `synchronized`. The per-call connection
strategy is deliberate: it is simple, thread-safe, and correct for
S2.5's scope. A future phase can introduce pooling if profiling shows
it matters.

### 1.6 WAL mode is set on every connection

Each `open()` sets `PRAGMA journal_mode=WAL` and
`PRAGMA busy_timeout=5000`. `journal_mode` is persistent (a one-time
DB setting) but harmless to re-apply. `busy_timeout` is per-connection.

### 1.7 Duplicate `step_id` throws

`appendStep` uses a plain `INSERT`. If `step_id` already exists, the
driver raises `SQLIntegrityConstraintViolationException`, which is
wrapped in `StorageException("append", ...)`. Silent upsert or
`INSERT OR REPLACE` is forbidden — a duplicate append is a caller bug,
not a normal case.

### 1.8 No ArchUnit, no jdeps on this module

`engine-store-sqlite` is Tier 1. The jdeps gate inspects only Tier 0
jars (S2.0 §3.9). The ArchUnit purity test applies only to Tier 0
modules. The only gate this module faces is the Enforcer in its
`pom.xml`. Do not add Tier 0 purity tests here.

### 1.9 Android target uses a different adapter

SQLite on Android uses `android.database.sqlite` / Room, not JDBC. The
Android `StoragePort` adapter (S9, deferred) will be a separate
implementation of the same interface. S2.5 is for server/SBC
deployments. Document this in the README.

## 2. Prerequisite

S2.4 merged to `main`. `mvn -B test` green on `main`. If not, stop and
report.

## 3. Scope

Deliver:

1. One change to `engine-store-sqlite/pom.xml` (add `sqlite-jdbc`)
2. Three source files in `engine-store-sqlite`
3. Two test files
4. Updated `package-info.java` and `README.md`
5. No `docs/INVARIANTS.md` update (S2.5 proves no named invariant; it
   is the base layer for S5's E4/E5 tests)
