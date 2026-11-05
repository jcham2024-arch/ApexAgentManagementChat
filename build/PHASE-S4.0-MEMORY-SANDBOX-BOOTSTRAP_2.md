# PHASE-S4.0-MEMORY-SANDBOX-BOOTSTRAP.md

**Version:** 1.0 · **Sub-phase of S4** · **Ends with module skeletons, enums, and interfaces compiling; `mvn test` green.**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S4-MEMORY-SANDBOX.md` (repo — router)
4. **This file**
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