# PHASE-S2.0-MODULE-SKELETONS.md

**Version:** 1.0 · **Sub-phase of S2** · **Ends with `mvn test` green**

## 0. Reading protocol

1. `AGENT_RULES.md`
2. `CONTEXT.md`
3. `PHASE-S2-KERNEL.md` (router)
4. **S2 context notes** (pasted by the human)
5. This file

## 1. Prerequisite

S1.2 is merged to `main`. All three gates pass. `apex-chat-common`
exists with `TenantId` and `CorePurityTest`. The Maven Enforcer rule is
in `apex/pom.xml`. If any is missing, stop and report.

## 2. Scope

S2.0 creates module skeletons only. Every module has:

- A `pom.xml` with the correct parent, dependency set, and Enforcer rule
- A `package-info.java` with `@NullMarked`
- An empty `src/main/java` and `src/test/java`
- A `README.md`: tier, allowed dependencies, ports consumed or
  implemented

No Java classes beyond `package-info.java`. No tests. No port
interfaces (S2.1). No planner (S2.2). No tools (S2.3).

Also: move the Enforcer rule from `apex/pom.xml` to `apex-chat/pom.xml`
and `apex-core/pom.xml`, so Tier 1 modules can have a narrower rule.
Narrow the CI `jdeps` gate to Tier 0 jars only. Create
`docs/INVARIANTS.md` stub.

## 3. Files to create or modify

### 3.1 Modify `apex/pom.xml`

Add to `<modules>`:
```
<module>apex-core</module>
<module>engine-tools-impl</module>
<module>engine-llm-http</module>
<module>engine-store-sqlite</module>
<module>engine-cli</module>
```

Remove the entire `<plugin>` block for `maven-enforcer-plugin` from
`<build><plugins>` (it moves to `apex-chat` and `apex-core`). Leave the
`<pluginManagement>` block unchanged.

### 3.2 Modify `apex-chat/pom.xml`

Add the `maven-enforcer-plugin` block (moved from `apex/pom.xml`) under
`<build><plugins>`. Verbatim.

### 3.3 Create `apex-core/pom.xml`

Parent: `apex`. Packaging: `pom`. Modules: `engine-core`,
`engine-planner`, `engine-tools`, `engine-store`. Has the same Enforcer
block as `apex-chat/pom.xml` (verbatim).

### 3.4 Create four Tier 0 module POMs

For each of `apex-core/engine-core`, `apex-core/engine-planner`,
`apex-core/engine-tools`, `apex-core/engine-store`:

- Parent: `apex-core` (inherits the Enforcer rule)
- Dependencies: `jspecify`, `junit-jupiter` (test), `assertj-core`
  (test)
- No other dependency

### 3.5 Create four Tier 1 module POMs

For each of `engine-tools-impl`, `engine-llm-http`,
`engine-store-sqlite`, `engine-cli`:

- Parent: `apex` (root, no Enforcer inherited)
- Add the `maven-enforcer-plugin` block **explicitly** to
  `<build><plugins>`. Its `<excludes>` list is the same as the Tier 0
  rule **plus** allow JDK-only imports (which are not Maven
  dependencies, so no change to the list). The list is:

```xml
<exclude>org.springframework*</exclude>
<exclude>org.springframework.boot*</exclude>
<exclude>org.springframework.ai*</exclude>
<exclude>com.google.cloud*</exclude>
<exclude>com.google.mcp*</exclude>
<exclude>org.embabel*</exclude>
<exclude>com.fasterxml.jackson*</exclude>
<exclude>io.micrometer*</exclude>
<exclude>com.zaxxer*</exclude>
```

- Dependencies: `jspecify`, `junit-jupiter` (test), `assertj-core`
  (test). Add `<dependency>` on `apex-core/engine-core` for
  `engine-tools-impl`, `engine-store-sqlite`, and `engine-cli`
  (version `${project.version}`).

### 3.6 Create `package-info.java` in every new module

Template:

```java
/**
 * <one-sentence module purpose>. Tier <0|1>. Ports consumed: <list or
 * "none">. Ports implemented: <list or "none">.
 */
@NullMarked
package com.apex.<module>;

import org.jspecify.annotations.NullMarked;
```

Module package paths: `com.apex.engine.core`, `com.apex.engine.planner`,
`com.apex.engine.tools`, `com.apex.engine.store`,
`com.apex.engine.tools.impl`, `com.apex.engine.llm.http`,
`com.apex.engine.store.sqlite`, `com.apex.engine.cli`.

### 3.7 Create `README.md` in every new module

Template:

```markdown
# <module-name>

**Tier:** <0|1> · **Purpose:** <one sentence>

## Allowed dependencies
<list>

## Ports consumed
<list or "none">

## Ports implemented
<list or "none">

## Tests
<none yet — S2.1+>
```

### 3.8 Create `docs/INVARIANTS.md`

List the invariant IDs with one-line descriptions. No test references
yet — each gets a test in its phase.

```markdown
# INVARIANTS.md

Testable claims. Each row's Test column is filled when the invariant's
phase arrives.

## Kernel (K)
| ID | Claim | Test |
|---|---|---|
| K1 | No dynamic JVM classloading | S3 |
| K2 | No shell interpolation | S2.3 |
| K3 | Kernel invariants are compiled constants | S2.2 |
| K4 | Canonical workspace root via toRealPath | S3 |
| K5 | Kill process tree via ProcessHandle.descendants | S2.3 |
| K6 | Cooperative cancellation ≤100ms | S2.2 |
| K7 | OS injection guarded | S3 |

## Evolution (E)
| ID | Claim | Test |
|---|---|---|
| E1 | Sentinel on every writeWorkspaceFile | S5 |
| E2 | Capability escalation requires grant | S3 |
| E3 | Sentinel failure → rollback | S5 |
| E4 | Commits cannot reference missing blobs | S5 |
| E5 | Branch CAS loses no writes | S5 |

## Contract (C)
| ID | Claim | Test |
|---|---|---|
| C1 | TS types isomorphic to Java OpenAPI | S6 |
| C2 | Zod validation at every boundary | S6 |
| C3 | OSC padding `(4 - (len % 4)) % 4` | S8 |
| C4 | ContextCompactor preserves SYSTEM + tool pairs | S4 |
```

### 3.9 Modify `.github/workflows/ci.yml`

Replace the `jdeps` step with a version that inspects only Tier 0 jars:

```yaml
- name: Gate G2 — jdeps bytecode purity (Tier 0 only)
  run: |
    set -euo pipefail
    mapfile -t TIER0_JARS < <(find apex-chat apex-core/engine-core \
      apex-core/engine-planner apex-core/engine-tools apex-core/engine-store \
      -path '*/target/*.jar' ! -name '*-sources.jar' \
      ! -name '*-javadoc.jar' 2>/dev/null)
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

## 4. Exit criteria

A. `mvn -B test` passes. CI green.
B. All nine modules build (apex-core group + 8 modules).
C. Tier 0 modules inherit the enterprise-ban from `apex-chat` or
   `apex-core`; Tier 1 modules declare the narrower ban explicitly.
D. `jdeps` gate inspects only Tier 0 jars.
E. `docs/INVARIANTS.md` exists with K1–K7, E1–E5, C1–C4.
F. No Java classes beyond `package-info.java`.
G. No file over 200 lines.

## 5. Do NOT add

- Any Java class other than `package-info.java`
- Any port interface (S2.1)
- Any planner code (S2.2)
- Any tool implementation (S2.3)
- Any test
- Any dependency other than JSpecify, JUnit 5, AssertJ
- Any file not named in §3

## 6. Commit

Three commits on `phase/s2.0-skeletons`:

1. `refactor(S2.0): move Enforcer rule from apex root to group parents`
2. `build(S2.0): add apex-core group and eight module skeletons`
3. `docs(S2.0): add INVARIANTS stub, narrow jdeps gate to Tier 0`

Push. Report CI link.