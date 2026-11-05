# PHASE-S2.0-MODULE-SKELETONS.md — Complete Session Package

**Project:** Apex Agent Management Chat
**Phase:** S2 (coding kernel), sub-phase S2.0 (module skeletons)
**This session's outcome:** Module structure exists, `mvn test` green, CI green
**Estimated files:** ~30 (mostly POMs, package-info, READMEs)

---

## 0. Reading protocol

Read in this exact order before writing any code:

1. `AGENT_RULES.md` (in repo, always-loaded behavior contract)
2. `CONTEXT.md` (in repo, always-loaded project map and file index)
3. **This file** (the complete S2.0 session instruction)

Do not begin until all three are read.

If any of the three is missing or unreadable, stop and report which.

---

## 1. Context notes — deltas between `CONTEXT.md` and this session

`CONTEXT.md` was written for the whole project. Some of it does not match the current state of the repo. These notes are the authoritative resolution for S2.0. Where a note conflicts with `CONTEXT.md`, this note wins. Do not stop over these — they are intentional, not errors.

### 1.1 The spec is not yet committed

`docs/apex-chat-spec/` is not in the repo. `CONTEXT.md` §2.C lists eleven spec files that do not exist yet.

**S2.0 does not need the spec.** Do not read it, do not create it, do not fail the reading protocol over its absence during S2.0. The spec will be pasted before S2.2 begins.

### 1.2 Eight new module names

S2 introduces modules that are not yet in `CONTEXT.md` §4. These are the authoritative module names for S2:

```
apex-core (parent aggregator)
apex-core/engine-core      (Tier 0)
apex-core/engine-planner   (Tier 0)
apex-core/engine-tools     (Tier 0)
apex-core/engine-store     (Tier 0)
engine-tools-impl          (Tier 1)
engine-llm-http            (Tier 1)
engine-store-sqlite        (Tier 1)
engine-cli                 (Tier 1)
```

`CONTEXT.md` §4 gets updated in S2.7 to reflect these. For S2.0, use these names exactly.

### 1.3 Enforcer rule restructure

S1.2 added the `maven-enforcer-plugin` block to `apex/pom.xml` (root parent). That block **moves in S2.0** to `apex-chat/pom.xml` and a new `apex-core/pom.xml`.

Tier 1 modules (`engine-tools-impl`, `engine-llm-http`, `engine-store-sqlite`, `engine-cli`) declare their own Enforcer block explicitly in their own `pom.xml`. The excluded groups are the same; only the location changes.

**Why:** Tier 0 modules must be provably pure. Tier 1 modules may use JDK I/O but must not use Spring/Google/Embabel/Jackson/Micrometer. Making each tier declare its ban in its own group parent means a future engineer cannot accidentally remove the ban by editing the wrong POM.

### 1.4 JDK imports are not Maven dependencies

Tier 1 modules use `java.io`, `java.nio.file`, `java.net.http`, `java.sql`. These are JDK modules, not Maven artifacts. They do not trigger the Enforcer. Do not add a Maven dependency for HTTP, SQLite, or file I/O. If a future sub-phase needs an external library, it goes into `docs/PORT-CATALOGUE.md` in S2.1.

### 1.5 CI `jdeps` gate narrowed to Tier 0

S2.0 §3.9 narrows the `jdeps` gate to Tier 0 jars only. If the gate stays broad, Tier 1 jars will fail for using `java.net.http` — which is allowed. Tier 1 has its own Enforcer block, so the `jdeps` narrowing is safe.

### 1.6 No tests in S2.0

The green CI at the end of S2.0 proves the module structure compiles. That is the only thing it proves. Do not add tests to "prove the skeleton works" — the skeleton has no behavior to test.

### 1.7 Module dependencies in S2.0

Only three Tier 1 modules declare a Maven dependency on `apex-core/engine-core`: `engine-tools-impl`, `engine-store-sqlite`, `engine-cli`. `engine-llm-http` does not — it depends only on the `ChatModelPort` interface, which lands in S2.1.

This means `engine-llm-http` will have an unused placeholder dependency on `engine-core` until S2.1 populates the interface. That is intentional. Add the dependency anyway so S2.1 does not need to touch the POM.

### 1.8 Prerequisite: S1.2 must be merged

S1.2 must be merged to `main`. `mvn -B test` must be green. `apex-chat-common` must exist with `TenantId` and `CorePurityTest`. The `maven-enforcer-plugin` block must currently be in `apex/pom.xml`.

If any of these is false, stop and report — do not proceed.

### 1.9 Existing directory

`docs/supplements/` may exist from an earlier plan. It is not authoritative. Do not read it, do not modify it, do not delete it in S2.0. A separate cleanup commit will handle it later.

---

## 2. Scope

S2.0 creates **module skeletons only**. Every new module has:

- A `pom.xml` with the correct parent, dependency set, and Enforcer configuration
- A `package-info.java` with `@NullMarked` and a one-paragraph purpose
- An empty `src/main/java` and `src/test/java` directory
- A `README.md` listing tier, allowed dependencies, ports consumed, ports implemented

No Java classes beyond `package-info.java`. No tests. No port interfaces (S2.1). No planner code (S2.2). No tool implementations (S2.3).

S2.0 also:
- Moves the Enforcer rule from `apex/pom.xml` to `apex-chat/pom.xml` and the new `apex-core/pom.xml`
- Narrows the CI `jdeps` gate to Tier 0 jars only
- Creates `docs/INVARIANTS.md` with a stub listing all invariant IDs (K1–K7, E1–E5, C1–C4) and "Test" column pointing to the phase that will implement each

---

## 3. Files to create or modify

All paths are relative to the repo root. Do not create files not listed here.

### 3.1 Modify `apex/pom.xml`

In `<modules>`, add:

```xml
<module>apex-core</module>
<module>engine-tools-impl</module>
<module>engine-llm-http</module>
<module>engine-store-sqlite</module>
<module>engine-cli</module>
```

Remove the **entire** `<plugin>` block for `maven-enforcer-plugin` from `<build><plugins>`. It moves to `apex-chat/pom.xml` and `apex-core/pom.xml`. Leave `<pluginManagement>` unchanged.

### 3.2 Modify `apex-chat/pom.xml`

Add the `maven-enforcer-plugin` block (moved from `apex/pom.xml`) under `<build><plugins>`. It is verbatim:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>3.4.1</version>
    <executions>
        <execution>
            <id>ban-enterprise-in-tier-0</id>
            <phase>validate</phase>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <bannedDependencies>
                        <excludes>
                            <exclude>org.springframework*</exclude>
                            <exclude>org.springframework.boot*</exclude>
                            <exclude>org.springframework.ai*</exclude>
                            <exclude>com.google.cloud*</exclude>
                            <exclude>com.google.mcp*</exclude>
                            <exclude>org.embabel*</exclude>
                            <exclude>com.fasterxml.jackson*</exclude>
                            <exclude>io.micrometer*</exclude>
                            <exclude>com.zaxxer*</exclude>
                        </excludes>
                        <searchTransitive>true</searchTransitive>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 3.3 Create `apex-core/pom.xml`

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
    <artifactId>apex-core</artifactId>
    <packaging>pom</packaging>
    <modules>
        <module>engine-core</module>
        <module>engine-planner</module>
        <module>engine-tools</module>
        <module>engine-store</module>
    </modules>
    <build>
        <plugins>
            <!-- The same maven-enforcer-plugin block as apex-chat/pom.xml,
                 verbatim. -->
        </plugins>
    </build>
</project>
```

### 3.4 Create four Tier 0 module POMs

For each of `apex-core/engine-core/pom.xml`, `apex-core/engine-planner/pom.xml`, `apex-core/engine-tools/pom.xml`, `apex-core/engine-store/pom.xml`:

- Parent: `apex-core`
- `<artifactId>` matches the module directory name
- Dependencies: `org.jspecify:jspecify`, `org.junit.jupiter:junit-jupiter` (test), `org.assertj:assertj-core` (test)
- No `<build>` section (inherits the Enforcer from `apex-core`)

Example for `apex-core/engine-core/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.apex</groupId>
        <artifactId>apex-core</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>engine-core</artifactId>
    <dependencies>
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

Repeat with the correct `<artifactId>` for `engine-planner`, `engine-tools`, `engine-store`.

### 3.5 Create four Tier 1 module POMs

For each of `engine-tools-impl/pom.xml`, `engine-llm-http/pom.xml`, `engine-store-sqlite/pom.xml`, `engine-cli/pom.xml`:

- Parent: `apex` (the root POM)
- `<artifactId>` matches the module directory name
- Dependencies: `org.jspecify:jspecify`, `org.junit.jupiter:junit-jupiter` (test), `org.assertj:assertj-core` (test), and a dependency on `apex-core/engine-core` (version `${project.version}`)
- `<build><plugins>` containing the same `maven-enforcer-plugin` block as `apex-chat/pom.xml`, verbatim

Example for `engine-cli/pom.xml`:

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
    <artifactId>engine-cli</artifactId>
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
    <build>
        <plugins>
            <!-- The same maven-enforcer-plugin block as apex-chat/pom.xml,
                 verbatim. -->
        </plugins>
    </build>
</project>
```

Repeat with the correct `<artifactId>` for `engine-tools-impl`, `engine-llm-http`, `engine-store-sqlite`.

**Note:** `engine-llm-http` declares the dependency on `engine-core` even though it has nothing to use yet. This is per context note §1.7. The dependency is unused until S2.1 populates `ChatModelPort`.

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