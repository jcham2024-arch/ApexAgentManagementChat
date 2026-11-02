# PHASE-S2.0-MODULE-SKELETONS-MODULES.md — Session Package, Part 3 of 3

**Phase:** S2 (coding kernel), sub-phase S2.0 (module skeletons).
**Part 3 of 3:** module files, CI gate, exit criteria, commits —
§3.6–§3.9 and §4–§6.

Parts: `PHASE-S2.0-MODULE-SKELETONS-SESSION.md` (protocol, deltas,
scope) · `PHASE-S2.0-MODULE-SKELETONS-POMS.md` (§3.1–§3.5) ·
`PHASE-S2.0-MODULE-SKELETONS-MODULES.md` (this file).

---
### 3.6 Create `package-info.java` in every new module

Path for each module: `<module>/src/main/java/com/apex/engine/<name>/package-info.java` where `<name>` is the sub-package:

| Module | Package |
|---|---|
| `apex-core/engine-core` | `com.apex.engine.core` |
| `apex-core/engine-planner` | `com.apex.engine.planner` |
| `apex-core/engine-tools` | `com.apex.engine.tools` |
| `apex-core/engine-store` | `com.apex.engine.store` |
| `engine-tools-impl` | `com.apex.engine.tools.impl` |
| `engine-llm-http` | `com.apex.engine.llm.http` |
| `engine-store-sqlite` | `com.apex.engine.store.sqlite` |
| `engine-cli` | `com.apex.engine.cli` |

Content template:

```java
/**
 * <one-sentence module purpose>. Tier <0|1>. Ports consumed: <list or
 * "none">. Ports implemented: <list or "none">.
 */
@NullMarked
package com.apex.engine.<name>;

import org.jspecify.annotations.NullMarked;
```

Fill `<one-sentence module purpose>` per module:

- `engine-core`: "Core loop, agent state machine, and port interfaces."
- `engine-planner`: "A* planner with GOAP preconditions and OODA replan loop."
- `engine-tools`: "Tool execution port and tool metadata."
- `engine-store`: "Persistence port interfaces for steps, sessions, workspace."
- `engine-tools-impl`: "Five kernel tools implementing the tool execution port."
- `engine-llm-http`: "OpenAI-compatible ChatModelPort implementation over java.net.http."
- `engine-store-sqlite`: "SQLite-backed StoragePort for step and session logs."
- `engine-cli`: "Task file loader and the headless kernel runner."

Fill "Ports consumed" and "Ports implemented" with "none (S2.1)" for S2.0. They will be populated in S2.1.

### 3.7 Create `README.md` in every new module

Path: `<module>/README.md`.

Template:

```markdown
# <module-name>

**Tier:** <0|1> · **Purpose:** <one sentence, matches package-info>

## Allowed dependencies
<list, or "java.base + jspecify only" for Tier 0>

## Ports consumed
none (S2.1)

## Ports implemented
none (S2.1)

## Tests
none yet — populated in S2.1+
```

### 3.8 Create `docs/INVARIANTS.md`

```markdown
# INVARIANTS.md — Testable Claims

Each invariant is a claim the code must satisfy. The Test column names
the phase that will implement the test. An invariant is not "proven"
until its test exists and is green.

## Kernel (K)
| ID | Claim | Test |
|---|---|---|
| K1 | No dynamic JVM classloading | S3 |
| K2 | No shell interpolation | S2.3 |
| K3 | Kernel invariants are compiled constants | S2.2 |
| K4 | Canonical workspace root via toRealPath | S3 |
| K5 | Kill process tree via ProcessHandle.descendants | S2.3 |
| K6 | Cooperative cancellation ≤100ms | S2.2 |
| K7 | OS injection guarded by dead-man + rate limit | S3 |

## Evolution (E)
| ID | Claim | Test |
|---|---|---|
| E1 | Sentinel runs on every writeWorkspaceFile | S5 |
| E2 | Capability escalation requires grant | S3 |
| E3 | Sentinel failure → atomic rollback | S5 |
| E4 | Commits cannot reference missing blobs | S5 |
| E5 | Branch CAS loses no writes | S5 |

## Contract (C)
| ID | Claim | Test |
|---|---|---|
| C1 | TS types isomorphic to Java OpenAPI | S6 |
| C2 | Zod validation at every TS/Java boundary | S6 |
| C3 | OSC padding `(4 - (len % 4)) % 4` | S8 |
| C4 | ContextCompactor preserves SYSTEM + tool pairs | S4 |
```

### 3.9 Modify `.github/workflows/ci.yml`

Replace the current "Gate G2 — jdeps bytecode purity" step with:

```yaml
- name: Gate G2 — jdeps bytecode purity (Tier 0 only)
  run: |
    set -euo pipefail
    mapfile -t TIER0_JARS < <(find \
      apex-chat apex-core/engine-core apex-core/engine-planner \
      apex-core/engine-tools apex-core/engine-store \
      -path '*/target/*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
      2>/dev/null)
    if [ ${#TIER0_JARS[@]} -eq 0 ]; then
      echo "no Tier 0 jars produced"; exit 0
    fi
    for jar in "${TIER0_JARS[@]}"; do
      echo "Inspecting $jar"
      if jdeps --ignore-missing-deps --multi-release 21 "$jar" \
         | grep -E 'org\.springframework|org\.embabel|com\.google\.cloud|com\.google\.mcp|com\.fasterxml\.jackson|io\.micrometer|com\.zaxxer'; then
        echo "GATE G2 FAILED: enterprise package in $jar"; exit 1
      fi
    done
    echo "GATE G2 PASSED"
```

---

## 4. Exit criteria

All must be true before you report "S2.0 complete":

- [ ] `mvn -B test` passes. CI is green on `phase/s2.0-skeletons`.
- [ ] Nine modules exist and build (`apex-core` aggregator plus the eight children).
- [ ] Tier 0 modules inherit the enterprise-ban Enforcer from `apex-chat` or `apex-core`. Verify by running `mvn -B validate` and confirming the Enforcer runs.
- [ ] Tier 1 modules declare the enterprise-ban Enforcer explicitly. Verify the same way.
- [ ] `jdeps` gate inspects only Tier 0 jars. If it fails on a Tier 1 jar, the narrowing was not applied correctly.
- [ ] `docs/INVARIANTS.md` exists with K1–K7, E1–E5, C1–C4 and the correct Test column.
- [ ] Every new module has `package-info.java` and `README.md`.
- [ ] No Java class other than `package-info.java` exists in any new module.
- [ ] No file exceeds 200 lines.
- [ ] No enterprise dependency (Spring, Google, Embabel, Jackson, Micrometer) appears anywhere.

---

## 5. Do NOT add

- Any Java class other than `package-info.java`
- Any port interface (`ChatModelPort`, `PlannerPort`, `ToolExecutorPort`, `ApprovalPort`, `StoragePort`, `VectorStorePort`, `SecretsPort`, `AuditPort`, `TelemetryPort`) — those land in S2.1
- Any planner code — S2.2
- Any tool implementation — S2.3
- Any test class — S2.1 onward
- Any dependency other than JSpecify, JUnit 5, AssertJ
- Any file not named in §3
- Any reference to `docs/apex-chat-spec/` in code or comments

---

## 6. Commit

Three commits on branch `phase/s2.0-skeletons`:

1. `refactor(S2.0): move Enforcer rule from apex root to group parents`
2. `build(S2.0): add apex-core group and eight module skeletons`
3. `docs(S2.0): add INVARIANTS stub, narrow jdeps gate to Tier 0`

Push the branch. Report:

- Commit hashes (all three)
- CI run link
- Confirmation that all exit criteria in §4 are met, one by one

Do not report "S2.0 complete" without the CI link. If CI fails, report the failure log and stop — do not attempt more than two fixes without human input.

---

**End of S2.0 session package.** Report status and stop.
