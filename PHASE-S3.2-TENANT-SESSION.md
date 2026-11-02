# PHASE-S3.2-TENANT-SESSION.md — Session Package, Part 1 of 7

**Version:** 1.0 · **Part 1 of 7:** reading protocol, context deltas, prerequisite, scope.
Parts: SESSION · CORE-PORT · SQLITE · POMS-RUNNER · CLI · TESTS ·
ISOLATION-CRITERIA, each `PHASE-S3.2-TENANT-<part>.md` (`AGENT_RULES.md`
§2.13).

---
## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S3.2

Authoritative where they conflict with any prior file. Do not stop over
them.

### 1.1 `StepLogEntry` gains `tenantId` — this is a breaking change

S2.1 defined `StepLogEntry(stepId, sessionId, index, stepType,
durationMs)`. S3.2 adds a `TenantId tenantId` field. **Every
construction site across the tree must be updated.** Search for
`new StepLogEntry(` and fix each one. Do not add a secondary
constructor or a builder to preserve backwards compatibility — the
whole point of this phase is that every append is tenant-scoped.

### 1.2 `StoragePort.stepsFor` becomes tenant-scoped — breaking

S2.1 defined `stepsFor(String sessionId)`. S3.2 changes it to
`stepsFor(TenantId tenantId, String sessionId)`. The SQLite adapter
filters by tenant. Cross-tenant queries return empty even if the
session ID is guessed. Update every call site.

### 1.3 `TenantId` lives in `apex-chat-common`

`com.apex.chat.common.identity.TenantId` (from S1.1). `engine-core`,
`engine-store-sqlite`, and `engine-cli` all reference it. Add explicit
`apex-chat-common` dependencies to `engine-store-sqlite/pom.xml` and
`engine-cli/pom.xml` (currently available only transitively through
`engine-core`).

### 1.4 SQLite schema migration is `ALTER TABLE`, not drop-and-recreate

Existing databases from S2.5 do not have a `tenant_id` column. The
`initialize()` method must detect the missing column via `PRAGMA
table_info(step_log)` and run `ALTER TABLE step_log ADD COLUMN
tenant_id TEXT NOT NULL DEFAULT 'legacy-migration'` on old DBs. New
DBs get the column via the updated `CREATE TABLE`. Existing rows are
assigned the sentinel `legacy-migration`, which no real tenant will
ever use — those rows become unreachable via normal queries, which is
correct: they were written before tenant scoping existed and should be
treated as legacy.

### 1.5 `Main` accepts `--tenant <id>` and `APEX_TENANT_ID` env

Priority order: `--tenant` flag > `APEX_TENANT_ID` env var > default
`"cli-default"`. The default keeps S2.6 and S2.7 tests working without
change. Document the flag in `engine-cli/README.md`.

### 1.6 `apex-tenant` stays empty in S3.2

The module exists (S3.0 skeleton) but receives no code in S3.2. The
isolation proof lives in `engine-store-sqlite` tests, directly against
the port. Postgres RLS advisors — the router's original scope for
`apex-tenant` — are deferred to S6 when the Postgres adapter is added.
S3.2's `apex-tenant` module exists only so `CONTEXT.md` §4 is accurate;
do not add code to it.

### 1.7 `RealTaskRunner` gains a `TenantId` constructor parameter

The runner uses one tenant for the whole run. The `TenantId` goes into
every `StepLogEntry` it writes. It does not call `stepsFor` in
production code, so the query-side change does not affect it — but its
tests do.

### 1.8 `PortTest` and `PortRecordsTest` need updating

`engine-core`'s `PortTest.storagePortIsImplementable` constructs an
anonymous `StoragePort` — the `stepsFor` signature changes. Update it.
`PortRecordsTest` constructs `StepLogEntry` in several tests — add
`tenantId` to each.

### 1.9 Spec not required

Router §4 confirms S3.2 does not need `docs/apex-chat-spec/`. Do not
read spec parts. Do not create spec files.

## 2. Prerequisite

S3.1 merged to `main`. `mvn -B test` green. `JwtAuthFilter`, `Principal`,
`RbacPolicy`, `DefaultRbacPolicy` exist in `apex-auth`. If not, stop and
report.

## 3. Scope

Deliver:

1. `engine-core`: `StepLogEntry` gains `tenantId`;
   `StoragePort.stepsFor` becomes tenant-scoped
2. `engine-store-sqlite`: schema migration, tenant column, tenant
   filter in queries
3. `engine-cli`: `RealTaskRunner` gains `TenantId`;
   `Main` parses `--tenant` / `APEX_TENANT_ID`
4. Explicit `apex-chat-common` dependencies in `engine-store-sqlite`
   and `engine-cli`
5. Three existing test classes updated (`PortTest`,
   `PortRecordsTest`, `SqliteStoragePortTest`,
   `SqliteStoragePortConcurrencyTest`, `RealTaskRunnerTest`)
6. One new test class: `TenantIsolationTest`
7. `engine-cli/README.md` updated with the `--tenant` flag
8. **No** `docs/INVARIANTS.md` changes (E2 is proven in S3.5;
   tenant isolation is an S3 exit criterion, not a named invariant)

## 4. Production files to modify
