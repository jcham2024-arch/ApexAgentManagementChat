# PHASE-S3.0-PLATFORM-BOOTSTRAP.md

**Version:** 1.0 · **Sub-phase of S3** · **Ends with platform
skeletons and typed `RiskLevel` compiling, `mvn test` green**

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

S2.7 merged to `main`. `mvn -B test` green on `main`. If not, stop and
report.

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

## 4. Files

### 4.1 Move `RiskLevel`

**Delete:** `engine-planner/src/main/java/com/apex/engine/planner/RiskLevel.java`

**Create:** `engine-core/src/main/java/com/apex/engine/core/port/model/RiskLevel.java`

```java
package com.apex.engine.core.port.model;

/**
 * Risk tier of an action. L0 is read-only; L5 is privilege escalation.
 * The planner weights higher-risk actions with a higher cost so a
 * lower-risk path is preferred when total base cost is equal. HITL
 * gating (S3.4) treats L0–L2 as auto-approve and L3–L5 as
 * approval-required.
 */
public enum RiskLevel {
    L0, L1, L2, L3, L4, L5;

    /** Planner cost penalty for this risk tier. */
    public double penalty() {
        return switch (this) {
            case L0 -> 0.0;
            case L1 -> 0.1;
            case L2 -> 0.3;
            case L3 -> 1.0;
            case L4 -> 3.0;
            case L5 -> 10.0;
        };
    }

    /** True if this tier requires human approval before execution. */
    public boolean requiresApproval() {
        return this == L3 || this == L4 || this == L5;
    }
}
```

**Update imports in:**
- `engine-planner/src/main/java/com/apex/engine/planner/Action.java`
  — change `import com.apex.engine.planner.RiskLevel;` to
  `import com.apex.engine.core.port.model.RiskLevel;` (if the file is
  in package `com.apex.engine.planner`, `RiskLevel` was resolved
  implicitly before; now it must be imported explicitly).
- `engine-planner/src/main/java/com/apex/engine/planner/AStarPlanner.java`
  — same import if the file references `RiskLevel`.
- Any `engine-planner` test that references `RiskLevel` — update to
  the new package.

Also add to `engine-planner/pom.xml` a dependency on `engine-core` **if
it is not already there**. S2.2 §4.3 added `engine-core` → `engine-planner`
(the reverse direction). If `engine-planner` now references a type in
`engine-core`, the dependency must point the other way, or both
directions. **This would create a cycle.**

**Resolution:** `engine-planner` currently does **not** depend on
`engine-core`; `engine-core` depends on `engine-planner`. Moving
`RiskLevel` to `engine-core.port.model` breaks that direction.

**Correct approach:** move `RiskLevel` to a **new module**
`engine-core`'s `port.model` package is inside `engine-core` which
depends on `engine-planner` — cycle.

**Alternative correct approach:** move `RiskLevel` to a **third
module**, or to `engine-planner` and have `engine-core` import from
there.

**Simplest correct approach:** leave `RiskLevel` in
`com.apex.engine.planner` where S2.2 put it. `engine-core` already
depends on `engine-planner` (S2.2 §4.3). `engine-core` can reference
`com.apex.engine.planner.RiskLevel` directly. Do **not** move it.

**Therefore:** the `RiskLevel` move is **cancelled**. `RiskLevel` stays
in `engine-planner`. `ApprovalRequest.riskLevel` becomes typed as
`com.apex.engine.planner.RiskLevel`. This is a one-way dependency
(`engine-core` → `engine-planner`) which already exists. No new
dependency, no cycle.

**Revised §4.1:** no move. Only the `ApprovalRequest` signature
changes. Add the `requiresApproval()` helper to the existing
`engine-planner` `RiskLevel` enum in place.

**Edit to `engine-planner/src/main/java/com/apex/engine/planner/RiskLevel.java`:**

Add this method to the existing enum:

```java
    /** True if this tier requires human approval before execution. */
    public boolean requiresApproval() {
        return this == L3 || this == L4 || this == L5;
    }
```

Do not change anything else in that file. Do not move it.

### 4.2 Update `ApprovalRequest`

**`engine-core/src/main/java/com/apex/engine/core/port/model/ApprovalRequest.java`**
— replace content:

```java
package com.apex.engine.core.port.model;

import com.apex.engine.planner.RiskLevel;

import java.util.Objects;

/**
 * A request for human approval of a tool call. {@link #riskLevel}
 * determines whether approval is required: L3–L5 require it, L0–L2 do
 * not (enforced by {@link RiskLevel#requiresApproval()}). The caller
 * (S3.4 HITL gate) is responsible for skipping requests on L0–L2.
 */
public record ApprovalRequest(String id, String summary, RiskLevel riskLevel) {
    public ApprovalRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(riskLevel, "riskLevel");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
```

**Update `engine-core/pom.xml`** — if it does **not** already declare a
dependency on `engine-planner`, add:

```xml
<dependency>
    <groupId>com.apex</groupId>
    <artifactId>engine-planner</artifactId>
    <version>${project.version}</version>
</dependency>
```

(S2.2 §4.3 said this dependency was added. Verify. If it is present,
do not duplicate.)

**Update call sites** — search for `new ApprovalRequest(` across the
tree. Every construction that passed a `String` risk level (e.g.,
`"L0"`, `"L3"`) must now pass `RiskLevel.L0`, `RiskLevel.L3`, etc.

Specific known call sites to check:
- `engine-core/src/test/java/.../PortRecordsTest.java` — the
  `ApprovalRequest` test constructs one; update it.
- `engine-tools-impl/src/test/java/.../KernelToolExecutorTest.java` —
  only if it constructs an `ApprovalRequest`. If not, leave it.
- `engine-cli/src/test/java/.../MainTest.java` — same.

If `PortRecordsTest` has a test like "rejects blank riskLevel" (from
S2.1's `String`-typed version), that test no longer applies — `null`
check remains, but blank-string check is gone. Replace with a null
check on `riskLevel`.

### 4.3 Create `apex-platform` parent POM

**`apex-platform/pom.xml`:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.apex</groupId>
        <artifactId>apex</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>apex-platform</artifactId>
    <packaging>pom</packaging>
    <modules>
        <module>apex-auth</module>
        <module>apex-tenant</module>
        <module>apex-audit</module>
    </modules>
</project>
```

**No Enforcer block.** `apex-platform` is Tier 2. The Enforcer ban is
inherited from `apex` root? Check `apex/pom.xml`. S2.0 §3.1 moved the
Enforcer from the root to `apex-chat` and `apex-core`. The root
`apex/pom.xml` has no Enforcer. So `apex-platform` and its children
have no Enforcer. That is correct — Tier 2 modules may use Spring.

**Update `apex/pom.xml`** — add to `<modules>`:

```xml
<module>apex-platform</module>
```

### 4.4 Create three Tier 2 module POMs

For each of `apex-platform/apex-auth/pom.xml`,
`apex-platform/apex-tenant/pom.xml`,
`apex-platform/apex-audit/pom.xml`:

- Parent: `apex-platform`
- `<artifactId>` matches the module directory name
- Dependencies: `jspecify`, `junit-jupiter` (test), `assertj-core`
  (test), `engine-core` (version `${project.version}`)
- No `<build>` section (no Enforcer)
- No Spring, Jackson, or other dependency in S3.0

Example for `apex-platform/apex-auth/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.apex</groupId>
        <artifactId>apex-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>apex-auth</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.apex</groupId>
            <artifactId>engine-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jspecify</groupId>
            <artifactId>jspecify</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Repeat for `apex-tenant`, `apex-audit`.

### 4.5 `package-info.java` in each new module

**`apex-platform/apex-auth/src/main/java/com/apex/platform/auth/package-info.java`:**
```java
/**
 * JWT parsing, RBAC policy, and authentication filter for the Apex
 * kernel. Tier 2. Ports consumed: none (S3.0 skeleton; auth filter
 * added in S3.1).
 */
@NullMarked
package com.apex.platform.auth;

import org.jspecify.annotations.NullMarked;
```

**`apex-platform/apex-tenant/src/main/java/com/apex/platform/tenant/package-info.java`:**
```java
/**
 * Tenant isolation: validators, RLS advisors, and StoragePort
 * tenant-scoping. Tier 2. Ports consumed: StoragePort (from S3.2).
 */
@NullMarked
package com.apex.platform.tenant;

import org.jspecify.annotations.NullMarked;
```

**`apex-platform/apex-audit/src/main/java/com/apex/platform/audit/package-info.java`:**
```java
/**
 * Chained audit log with PII redaction. Tier 2. Implements AuditPort
 * (from S3.3).
 */
@NullMarked
package com.apex.platform.audit;

import org.jspecify.annotations.NullMarked;
```

### 4.6 `README.md` in each new module

Template (fill in per module):

```markdown
# <module-name>

**Tier:** 2 · **Purpose:** <one sentence matching package-info>

## Allowed dependencies
any (Tier 2)

## Ports consumed
<list or "none">

## Ports implemented
<list or "none">

## Tests
none yet — populated in S3.<N>
```

Fill in:
- `apex-auth` — Purpose: JWT, RBAC, auth filter. Consumes: none (S3.0). Implements: none. Tests: S3.1.
- `apex-tenant` — Purpose: tenant isolation and RLS. Consumes: StoragePort (S3.2). Implements: none. Tests: S3.2.
- `apex-audit` — Purpose: chained audit log, PII redaction. Consumes: none. Implements: AuditPort (S3.3). Tests: S3.3.

### 4.7 Update `CONTEXT.md` §4

Add four rows to the module table (or verify they exist):

| Module | Tier | Role |
|---|---|---|
| `apex-platform` | parent | Aggregator for platform security modules |
| `apex-platform/apex-auth` | 2 | JWT, RBAC, auth filter |
| `apex-platform/apex-tenant` | 2 | Tenant isolation, RLS |
| `apex-platform/apex-audit` | 2 | Chained audit log, PII redaction |

If already present, verify and leave. If missing, add.

### 4.8 Update `docs/INVARIANTS.md`

Change the Test column for these rows:

| ID | Before | After |
|---|---|---|
| K1 | S3 | S3.5 pending |
| K3 | S3 | S3.5 pending |
| K4 | S3 | S3.5 pending |
| K7 | S3 | S3.5 pending |
| E2 | S3 | S3.5 pending |

Do not change other rows.

## 5. Tests

S3.0 adds no new tests of its own. All existing tests must still pass
after the `ApprovalRequest` signature change. Specific tests to verify:

1. `engine-core` `PortTest` — one `@Test` per port, still passes
   (single-method ports use lambdas; `ApprovalPort` still works because
   the interface signature did not change, only the request record's
   fields).
2. `engine-core` `PortRecordsTest` — update the `ApprovalRequest` test
   for the new `RiskLevel` field. If a test asserted "rejects blank
   riskLevel", replace it with "rejects null riskLevel".
3. `engine-planner` `AStarPlannerTest` — still passes (RiskLevel not
   moved, only extended).
4. All other tests — unaffected.

If any test fails due to a downstream effect of the `ApprovalRequest`
signature change, fix the call site, do not change the type.

## 6. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `RiskLevel` has `requiresApproval()`; still lives in
      `com.apex.engine.planner`; not moved.
- [ ] `ApprovalRequest.riskLevel` is typed as `RiskLevel`.
- [ ] `engine-core/pom.xml` declares a dependency on `engine-planner`
      (verify it was added in S2.2; add if not).
- [ ] `apex-platform` parent + three child modules exist and build.
- [ ] Each new module has `package-info.java` with `@NullMarked` and
      a `README.md`.
- [ ] `apex/pom.xml` `<modules>` includes `apex-platform`.
- [ ] `CONTEXT.md` §4 lists the four new module rows.
- [ ] `docs/INVARIANTS.md` K1, K3, K4, K7, E2 Test columns say
      `S3.5 pending`.
- [ ] No Spring, Jackson, Micrometer, Google, or Embabel dependency
      added anywhere.
- [ ] No file over 200 lines.
- [ ] All ArchUnit Tier 0 gates still pass.
- [ ] `jdeps` gate inspects only Tier 0 jars (unchanged from S2.0).

## 7. Do NOT add

- Any source file in the platform modules beyond `package-info.java`
- Any Spring, Jackson, Micrometer, Google, or Embabel dependency
- Any JWT library, crypto library, or audit signing library
- Any port implementation in the platform modules (S3.1–S3.3)
- Any change to `engine-tools-impl`, `engine-llm-http`,
  `engine-store-sqlite`, `engine-planner` (beyond the `RiskLevel`
  `requiresApproval()` addition), or `engine-cli`
- Any spec file
- Any file not listed in §4

## 8. Commit

Three commits on `phase/s3.0-platform-bootstrap`:

1. `feat(S3.0): add requiresApproval to RiskLevel, type ApprovalRequest`
2. `build(S3.0): add apex-platform aggregator and three module skeletons`
3. `docs(S3.0): update CONTEXT.md module table, INVARIANTS.md S3.5 placeholders`

Push. Report CI link and confirmation that:
- All existing tests still pass after the `ApprovalRequest` change
- `PortRecordsTest` was updated for the `RiskLevel` field
- No enterprise dependency was added
- The four new modules build

If CI fails twice on the same root cause, stop and report the log.

---

**End of S3.0 session package.**