# PHASE-S4.0-MEMORY-SANDBOX-BOOTSTRAP.md

**Version:** 1.0 · **Sub-phase of S4** · **Ends with module skeletons,
enums, and interfaces compiling; `mvn test` green**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md`
2. `CONTEXT.md`
3. `PHASE-S4-MEMORY-SANDBOX.md` (router)
4. **This file**

## 1. Context notes — deltas for S4.0

Authoritative where they conflict with any prior file. Do not stop over
them.

### 1.1 Two new modules, no other code changes

S4.0 creates `engine-memory` (Tier 0) and `engine-sandbox` (Tier 1).
No existing module is touched. No behavior beyond records and
interfaces.

### 1.2 Memory does not replace `StoragePort` or `VectorStorePort`

`MemoryRouter` composes over them. Episodic memory (S4.2) wraps
`StoragePort`. Semantic memory (S4.3) wraps `VectorStorePort`. Working
memory (S4.1) is a new in-memory buffer. The router is the composition
point; the ports stay untouched.

### 1.3 `ContextCompactor` is interface only

Signature defined here. Implementation and C4 proof land in S4.5. Do
not implement.

### 1.4 `SandboxPort` is interface only

Signature and `SandboxTier` defined here. Process tier lands in S4.6.
Do not implement.

### 1.5 Tier discipline

`engine-memory` is Tier 0: `java.base` + `org.jspecify` only. It
**must** carry the Enforcer block from `apex-chat/pom.xml`, verbatim.

`engine-sandbox` is Tier 1: may use `java.nio.file` (Path is in the
public API). It **must** carry the Enforcer block from
`engine-tools-impl/pom.xml`, verbatim — same exclusions as Tier 0,
declared inline.

### 1.6 `INVARIANTS.md` unchanged

C4 proof is S4.5. Do not touch `docs/INVARIANTS.md`.

### 1.7 No spec needed

Router §4 confirms.

## 2. Prerequisite

S3.5 merged to `main`. `mvn -B test` green on `main`. If not, stop
and report.

## 3. Scope

Deliver:

1. `apex/pom.xml` updated: two new modules
2. Two new module POMs
3. Six types in `engine-memory`
4. Four types in `engine-sandbox`
5. `package-info.java` and `README.md` in both modules
6. `CONTEXT.md` §4: two new rows
7. Two test classes

## 4. Files

### 4.1 `apex/pom.xml`

In `<modules>`, after `engine-store-sqlite`:

```xml
<module>engine-memory</module>
<module>engine-sandbox</module>
```

### 4.2 `engine-memory/pom.xml`

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
    <artifactId>engine-memory</artifactId>
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
    <build>
        <plugins>
            <!-- maven-enforcer-plugin block, verbatim from apex-chat/pom.xml -->
        </plugins>
    </build>
</project>
```

### 4.3 `engine-sandbox/pom.xml`

Same structure, but `<artifactId>engine-sandbox</artifactId>` and the
Enforcer block copied verbatim from `engine-tools-impl/pom.xml`
(Tier 1 style — same exclusions, declared inline).

### 4.4 `engine-memory` types (package `com.apex.engine.memory`)

**MemoryKind.java**
```java
package com.apex.engine.memory;

/**
 * Kinds of memory the router can query. WORKING is the current
 * conversation buffer; EPISODIC is past steps and sessions; SEMANTIC
 * is facts, decisions, and embeddings.
 */
public enum MemoryKind {
    WORKING,
    EPISODIC,
    SEMANTIC
}
```

**MemoryQuery.java**
```java
package com.apex.engine.memory;

import java.util.Objects;
import java.util.Set;

/**
 * A memory retrieval request. {@code kinds} names which backends to
 * consult; empty means none (rejected). {@code maxHits} caps the total
 * results returned across all consulted backends.
 */
public record MemoryQuery(String text, Set<MemoryKind> kinds, int maxHits) {
    public MemoryQuery {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kinds, "kinds");
        kinds = Set.copyOf(kinds);
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("kinds must not be empty");
        }
        if (maxHits <= 0) {
            throw new IllegalArgumentException("maxHits must be positive");
        }
    }
}
```

**MemoryHit.java**
```java
package com.apex.engine.memory;

import java.util.Objects;

/**
 * A single result from a memory retrieval. {@code score} is a
 * relevance score in [0, ∞); higher is better. Fusion across
 * heterogeneous backends normalizes scores via reciprocal rank fusion
 * in the router implementation, so scores are only comparable within
 * the same {@code kind}.
 */
public record MemoryHit(MemoryKind kind, String source, String content,
                        double score) {
    public MemoryHit {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(content, "content");
        if (score < 0) {
            throw new IllegalArgumentException("score must be >= 0");
        }
        if (source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
    }
}
```

**MemoryResult.java**
```java
package com.apex.engine.memory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The result of a memory retrieval. {@code queried} names which
 * backends were actually consulted (a subset of the query's
 * {@code kinds} — a backend may be unavailable and skipped).
 */
public record MemoryResult(List<MemoryHit> hits, Set<MemoryKind> queried) {
    public MemoryResult {
        Objects.requireNonNull(hits, "hits");
        Objects.requireNonNull(queried, "queried");
        hits = List.copyOf(hits);
        queried = Set.copyOf(queried);
    }
}
```

**MemoryRouter.java**
```java
package com.apex.engine.memory;

/**
 * Retrieves memory hits across one or more backends, fuses them by
 * relevance, and returns up to {@code query.maxHits()}. Implementations
 * select which backends to consult based on {@code query.kinds()}, skip
 * unavailable backends, and return a stable, deterministic result for
 * the same query and backends. Tier 0.
 */
public interface MemoryRouter {
    MemoryResult route(MemoryQuery query);
}
```

**ContextCompactor.java**
```java
package com.apex.engine.memory;

import java.util.List;

/**
 * Compacts a conversation history to fit within a token budget while
 * preserving invariant C4: the SYSTEM message at index 0 is never
 * dropped, and any tool-call/result pair is preserved or dropped as a
 * unit, never split.
 *
 * <p>The history is a {@code List<String>} where index 0 is the
 * system prompt, index 1 begins with {@code "TASK: "}, and indices 2+
 * alternate {@code "ASSISTANT: "} and {@code "TOOL_RESULT: "} turns.
 * Implementations must respect that convention.
 */
public interface ContextCompactor {
    List<String> compact(List<String> history, int maxTokens);
}
```

**package-info.java**
```java
/**
 * Memory retrieval and compaction for the Apex kernel. Tier 0.
 * Defines the memory router, hit types, and the context compactor
 * contract. Implementations live in S4.1 (working), S4.2 (episodic),
 * S4.3 (semantic), S4.4 (router), S4.5 (compactor).
 */
@NullMarked
package com.apex.engine.memory;

import org.jspecify.annotations.NullMarked;
```

**README.md**
```markdown
# engine-memory

**Tier:** 0 · **Purpose:** Memory retrieval and compaction.

## Allowed dependencies
java.base + jspecify only

## Ports consumed
none in S4.0 — S4.2 wraps StoragePort, S4.3 wraps VectorStorePort

## Ports implemented
none — MemoryRouter is a service, not a port

## Types
- `MemoryKind` — WORKING / EPISODIC / SEMANTIC
- `MemoryQuery` — retrieval request
- `MemoryHit` — single result
- `MemoryResult` — fused result set
- `MemoryRouter` — retrieval service interface
- `ContextCompactor` — history compaction interface

## Tests
MemoryTypesTest
```

### 4.5 `engine-sandbox` types (package `com.apex.engine.sandbox`)

**SandboxTier.java**
```java
package com.apex.engine.sandbox;

/**
 * Isolation levels for tool execution. PROCESS uses ProcessBuilder
 * with a realpath jail and process-tree kill. CONTAINER uses gVisor
 * (runsc) inside Docker. FIRE_CRACKER uses a microVM. Tier selection
 * is by RiskLevel: L0–L2 → PROCESS, L3–L4 → CONTAINER, L5 →
 * FIRE_CRACKER. Container and Firecracker implementations land in S6;
 * S4.6 proves the selection logic only.
 */
public enum SandboxTier {
    PROCESS,
    CONTAINER,
    FIRE_CRACKER
}
```

**SandboxCommand.java**
```java
package com.apex.engine.sandbox;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A command to execute in a sandbox. {@code binaryPath} must be an
 * absolute path to an allowlisted binary. {@code args} are passed as a
 * tokenized vector — never through a shell. {@code cwd} must exist and
 * be inside the sandbox's authorized root.
 */
public record SandboxCommand(Path binaryPath, List<String> args,
                             Path cwd, int timeoutSeconds) {
    public SandboxCommand {
        Objects.requireNonNull(binaryPath, "binaryPath");
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(cwd, "cwd");
        args = List.copyOf(args);
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        if (!binaryPath.isAbsolute()) {
            throw new IllegalArgumentException("binaryPath must be absolute");
        }
        if (!cwd.isAbsolute()) {
            throw new IllegalArgumentException("cwd must be absolute");
        }
    }
}
```

**SandboxResult.java**
```java
package com.apex.engine.sandbox;

import java.util.Objects;

/**
 * Result of a sandboxed execution. {@code exitCode} is 0 on success.
 * {@code output} is the merged stdout+stderr, capped at the sandbox's
 * output limit. {@code timedOut} is true if the sandbox was killed for
 * exceeding its timeout.
 */
public record SandboxResult(int exitCode, String output, boolean timedOut) {
    public SandboxResult {
        Objects.requireNonNull(output, "output");
    }
}
```

**SandboxPort.java**
```java
package com.apex.engine.sandbox;

/**
 * Executes a command inside a specific isolation tier. Each tier has
 * one implementation; the caller selects the tier by the tool's risk
 * level. Implementations are responsible for their own isolation
 * mechanism (ProcessBuilder, Docker+gVisor, Firecracker). Tier 1.
 */
public interface SandboxPort {
    SandboxTier tier();
    SandboxResult execute(SandboxCommand command);
}
```

**package-info.java**
```java
/**
 * Sandboxed tool execution across three isolation tiers. Tier 1.
 * Defines the sandbox port and command/result types. Process tier
 * lands in S4.6; container and Firecracker stubs land in S4.6;
 * production isolation lands in S6.
 */
@NullMarked
package com.apex.engine.sandbox;

import org.jspecify.annotations.NullMarked;
```

**README.md**
```markdown
# engine-sandbox

**Tier:** 1 · **Purpose:** Sandboxed tool execution across isolation tiers.

## Allowed dependencies
java.base (including java.nio.file), jspecify

## Ports consumed
none in S4.0 — S4.6 will consume SandboxedProcessExecutor

## Ports implemented
SandboxPort (per tier) — implementations land in S4.6

## Types
- `SandboxTier` — PROCESS / CONTAINER / FIRE_CRACKER
- `SandboxCommand` — binary, args, cwd, timeout
- `SandboxResult` — exit code, output, timedOut
- `SandboxPort` — per-tier execution interface

## Tests
SandboxTypesTest
```

### 4.6 Tests

**`engine-memory/src/test/java/com/apex/engine/memory/MemoryTypesTest.java`**

Cover:
1. `MemoryQuery` rejects null text
2. `MemoryQuery` rejects blank text
3. `MemoryQuery` rejects empty kinds
4. `MemoryQuery` rejects `maxHits <= 0`
5. `MemoryQuery` defensively copies kinds (`UnsupportedOperationException` on mutate)
6. `MemoryHit` rejects null kind/source/content
7. `MemoryHit` rejects `score < 0`
8. `MemoryHit` rejects blank source
9. `MemoryResult` defensively copies hits and queried
10. `MemoryResult` accepts empty hits with non-empty queried

**`engine-sandbox/src/test/java/com/apex/engine/sandbox/SandboxTypesTest.java`**

Cover:
1. `SandboxCommand` rejects null binaryPath/args/cwd
2. `SandboxCommand` rejects relative binaryPath
3. `SandboxCommand` rejects relative cwd
4. `SandboxCommand` rejects `timeoutSeconds <= 0`
5. `SandboxCommand` defensively copies args
6. `SandboxResult` rejects null output
7. `SandboxResult` accepts any exit code (including negative)
8. `SandboxTier` has exactly three values (PROCESS, CONTAINER, FIRE_CRACKER)

### 4.7 `CONTEXT.md` §4

Add two rows to the module table:

| Module | Tier | Role |
|---|---|---|
| `engine-memory` | 0 | Memory types, router, compactor |
| `engine-sandbox` | 1 | Sandbox port, tier implementations |

If the table uses a different column layout, match it. Do not
restructure.

## 5. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] Both new modules build and are in `apex/pom.xml` `<modules>`.
- [ ] Six memory types exist in `com.apex.engine.memory`.
- [ ] Four sandbox types exist in `com.apex.engine.sandbox`.
- [ ] `MemoryTypesTest` passes all 10 tests.
- [ ] `SandboxTypesTest` passes all 8 tests.
- [ ] Enforcer passes on both modules (Tier 0 block on `engine-memory`,
      Tier 1 block on `engine-sandbox`).
- [ ] `jdeps` gate (Tier 0 only) still passes on `engine-memory` jar.
- [ ] `CONTEXT.md` §4 gains two rows.
- [ ] No file exceeds 200 lines.
- [ ] No enterprise dependency anywhere.
- [ ] `docs/INVARIANTS.md` unchanged.
- [ ] No spec files read or created.
- [ ] No other module modified.

## 6. Do NOT add

- Any implementation of `MemoryRouter`, `ContextCompactor`, or
  `SandboxPort`
- Any dependency on `engine-core`, `engine-tools-impl`,
  `engine-store-sqlite`, or `engine-cli`
- Any Spring, Jackson, Micrometer, Google, or Embabel dependency
- Any change to `apex-chat/*`, `apex-core/*`, `apex-platform/*`, or
  `engine-cli`
- Any spec file
- Any file not listed in §4 or §5

## 7. Commit

Three commits on `phase/s4.0-memory-sandbox-bootstrap`:

1. `build(S4.0): add engine-memory and engine-sandbox modules`
2. `feat(S4.0): add memory types and interfaces (router, compactor)`
3. `feat(S4.0): add sandbox types and interface; update CONTEXT.md`

Push. Report CI link, per-test-class pass counts, and confirm:
- Both new modules appear in `apex/pom.xml` `<modules>`
- `MemoryTypesTest` and `SandboxTypesTest` pass
- No enterprise dependency added
- `CONTEXT.md` §4 updated

If CI fails twice on the same root cause, stop and report the log.

---

**End of S4.0 session package.**