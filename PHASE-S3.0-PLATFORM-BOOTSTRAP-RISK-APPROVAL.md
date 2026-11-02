# PHASE-S3.0-PLATFORM-BOOTSTRAP-RISK-APPROVAL.md — Session Package, Part 2 of 4

**Version:** 1.0 · **Part 2 of 4:** §4.1–§4.2, RiskLevel and the typed ApprovalRequest.
Parts: SESSION · RISK-APPROVAL · PLATFORM · TESTS-CRITERIA, each
`PHASE-S3.0-PLATFORM-BOOTSTRAP-<part>.md` (`AGENT_RULES.md` §2.13).

---
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
