# PHASE-S4.1-WORKING-MEMORY.md

**Version:** 1.0 · **Sub-phase of S4** · **Ends with a bounded working memory backend proven by tests.**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S4-MEMORY-SANDBOX.md` (repo — router)
4. `PHASE-S4.0-MEMORY-SANDBOX-BOOTSTRAP_1.md` (S4.0 bootstrap)
5. **This file**
# PHASE-S4.1-WORKING-MEMORY.md

**Version:** 1.0 · **Sub-phase of S4** · **Ends with a bounded working
memory backend proven by tests**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S4-MEMORY-SANDBOX.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S4.1

Authoritative where they conflict with any prior file. Do not stop over
them.

### 1.1 S4.0 was missing `MemoryBackend`

S4.0 defined `MemoryRouter` (the fusion service) but not the backend
interface that each memory kind implements. Without it, S4.4's router
cannot accept a list of backends, and S4.2/S4.3 have no contract to
implement. **S4.1 adds `MemoryBackend`** to `com.apex.engine.memory`.
This is a one-file, one-interface addition — the minimum S4.0 should
have included.

### 1.2 Working memory is not the runner's history list

`RealTaskRunner` (S3.4) keeps its own `List<String> history`. Working
memory is a separate, bounded, queryable store. They are not required
to be synchronized in S4.1. A future phase (S4.5 compactor, S6 server)
may choose to derive the history list from working memory, but that is
not this phase's scope.

### 1.3 Capacity is entry count, not tokens

Token budget enforcement lives in the compactor (S4.5). Working memory
bounds **entry count** — a simple, testable property. `maxEntries=20`
means the buffer holds at most 20 entries; the 21st evicts the oldest.

### 1.4 Eviction is FIFO, not LRU

A sliding-window context loses the oldest turn first. FIFO matches
that. LRU would require access timestamps and a comparison on every
query; the added complexity buys nothing until S4.5 proves the window
is the bottleneck. LRU is deferred, not forgotten.

### 1.5 Query is case-insensitive substring

No embeddings, no tokenization, no stemmers. Working memory is small
(few dozen entries) and matches on the raw text the runner produced.
Score is tiered:

- Exact case-sensitive substring: `1.0`
- Case-insensitive substring: `0.8`
- No match: not a hit

This is deterministic and testable. Semantic search is S4.3.

### 1.6 Thread-safe via `synchronized`

The runner is single-threaded per task, but concurrent `RealTaskRunner`
instances may share a working memory in S6. Use `synchronized` on
`add`, `query`, `entries`, `size`, `clear`. The cost is negligible and
the correctness is unconditional.

### 1.7 `MemoryRouter` unchanged

S4.0's router interface stays exactly as specified. S4.4 will build
`DefaultMemoryRouter(List<MemoryBackend>)`; S4.1 provides the first
`MemoryBackend` implementation.

### 1.8 Spec not required

Router §4 confirms S4.1 does not need `docs/apex-chat-spec/`. Do not
read spec parts. Do not create spec files.

### 1.9 `INVARIANTS.md` unchanged

C4 is proven in S4.5. Working memory has no named invariant.

## 2. Prerequisite

S4.0 merged to `main`. `mvn -B test` green. `engine-memory` exists with
`MemoryKind`, `MemoryQuery`, `MemoryHit`, `MemoryResult`,
`MemoryRouter`, `ContextCompactor`. If any is missing, stop and report.

## 3. Scope

Deliver:

1. `MemoryBackend` interface in `engine-memory`
2. `WorkingMemoryEntry` record in `engine-memory`
3. `BoundedWorkingMemory` class in `engine-memory`
4. Update `package-info.java` and `README.md` for `engine-memory`
5. One test class: `BoundedWorkingMemoryTest`
6. No other module touched. No `CONTEXT.md` change (already lists
   `engine-memory`).

## 4. Production files

### 4.1 `engine-memory/src/main/java/com/apex/engine/memory/MemoryBackend.java`

```java
package com.apex.engine.memory;

/**
 * A single memory backend. Each implementation represents one
 * {@link MemoryKind} and answers a {@link MemoryQuery} against that
 * backend only. The router (S4.4) composes multiple backends and
 * fuses their results.
 *
 * <p>Implementations must not consult other backends. They must return
 * an empty {@link MemoryResult} when the query's {@code kinds} does
 * not include their {@link #kind()}. They must be thread-safe for
 * concurrent reads and appends, or document otherwise.
 */
public interface MemoryBackend {

    /** The kind of memory this backend serves. */
    MemoryKind kind();

    /**
     * Retrieves hits from this backend. Returns at most
     * {@code query.maxHits()} hits. Returns empty if this backend's
     * kind is not in {@code query.kinds()}.
     */
    MemoryResult query(MemoryQuery query);
}
```

### 4.2 `engine-memory/src/main/java/com/apex/engine/memory/WorkingMemoryEntry.java`

```java
package com.apex.engine.memory;

import java.time.Instant;
import java.util.Objects;

/**
 * A single entry in working memory. {@code role} names the speaker or
 * category, using the same conventions as the runner's history
 * prefixes: {@code "system"}, {@code "task"}, {@code "assistant"},
 * {@code "tool_result"}, {@code "error"}. The compactor (S4.5) uses
 * role to identify SYSTEM messages and tool-call/result pairs.
 *
 * <p>{@code at} is the wall-clock time of the entry; it is not used
 * for query scoring in S4.1, but future phases may weight recent
 * entries more heavily.
 */
public record WorkingMemoryEntry(String role, String content, Instant at) {
    public WorkingMemoryEntry {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(at, "at");
        if (role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }
}
```

### 4.3 `engine-memory/src/main/java/com/apex/engine/memory/BoundedWorkingMemory.java`

```java
package com.apex.engine.memory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Bounded FIFO working memory. Stores at most {@code maxEntries}
 * entries; the oldest is evicted when the buffer overflows. Query is
 * case-insensitive substring match with tiered scoring:
 *
 * <ul>
 *   <li>exact case-sensitive substring → score 1.0</li>
 *   <li>case-insensitive substring → score 0.8</li>
 *   <li>no match → not a hit</li>
 * </ul>