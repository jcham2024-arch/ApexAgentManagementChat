# PHASE-S3.0-PLATFORM-BOOTSTRAP-SESSION.md — Session Package, Part 1 of 4

**Version:** 1.0 · **Part 1 of 4:** reading protocol, context deltas, prerequisite, scope.
Parts: SESSION · RISK-APPROVAL · PLATFORM · TESTS-CRITERIA, each
`PHASE-S3.0-PLATFORM-BOOTSTRAP-<part>.md` (`AGENT_RULES.md` §2.13).

---
## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S3.0

Authoritative where they conflict with any prior file. Do not stop
over them.

### 1.1 `RiskLevel` moves from `engine.planner` to `engine.port.model`

S2.2 introduced `com.apex.engine.planner.RiskLevel` with the values
L0–L5 and `penalty()`. It now belongs in the shared port model because
`ApprovalRequest` (S2.1) needs it and `apex-auth` (S3.1) will reference
it. Move the enum. Update imports in:
- `engine-planner`: `Action.java`, `AStarPlanner.java`
- `engine-planner` tests that reference `RiskLevel`

Do **not** keep two copies. Do **not** leave a re-export shim in
`engine.planner`. Move it. Three files change imports per module.

### 1.2 `ApprovalRequest.riskLevel` changes from `String` to `RiskLevel`

S2.1 defined `ApprovalRequest(String id, String summary, String
riskLevel)`. In S3.0, `riskLevel` becomes `RiskLevel`. This is a
**breaking change** — the record signature changes, and any test that
constructed an `ApprovalRequest` with `"L0"` must now pass
`RiskLevel.L0`.

Files affected:
- `engine-core/src/main/java/.../port/model/ApprovalRequest.java`
- `engine-core/src/test/java/.../port/model/PortRecordsTest.java`
- `engine-tools-impl/src/test/java/.../KernelToolExecutorTest.java`
  (if it constructs an `ApprovalRequest`)
- `engine-cli/src/test/java/.../MainTest.java` (if it does)

Search the tree for `new ApprovalRequest(` and update every call site.

### 1.3 No spec parts needed

`PHASE-S3-SECURITY.md` §4 confirms S3 does not need the spec. Do not
read spec parts. Do not create spec files.

### 1.4 `apex-platform` is Tier 2

All three platform modules (`apex-auth`, `apex-tenant`, `apex-audit`)
are Tier 2. They **may** add Spring, Jackson, or Micrometer in later
sub-phases. S3.0 adds **no** external dependencies — the skeletons
compile with only `jspecify`, `junit-jupiter`, `assertj-core`, and
`engine-core` (for `RiskLevel` and later types).

### 1.5 `CONTEXT.md` §4 must be updated

The module list in `CONTEXT.md` §4 currently names `apex-platform/*` as
Tier 2 with the roles JWT/RLS/audit. S3.0 makes that real. Add the
four new module rows (aggregator + three children) to §4 if they are
not already there. This is a small doc edit, not a source-code change.

### 1.6 `INVARIANTS.md` rows get test-name placeholders

S3.5 fills in the actual tests for K1, K3, K4, K7, E2. S3.0 updates
`docs/INVARIANTS.md` so the Test column for those rows says
`S3.5 pending` rather than `S3`. This is documentation only.

## 2. Prerequisite

S2.7 merged to `main`. `mvn -B test` green on `main`. If any is false,
stop and report.

## 3. Scope

Deliver:

1. `RiskLevel` moved to `engine-core.port.model`; imports updated
2. `ApprovalRequest.riskLevel` changed to typed `RiskLevel`; call sites
   updated
3. `apex-platform` parent POM + three child module skeletons
4. `package-info.java` and `README.md` in each new module
5. `CONTEXT.md` §4 updated with the four new rows
6. `docs/INVARIANTS.md` Test column for K1/K3/K4/K7/E2 set to `S3.5
   pending`

No business logic. No Spring. No Jackson. No code beyond
`package-info.java` in the platform modules.
