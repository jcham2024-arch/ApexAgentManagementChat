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
 *
 * <p>All public methods are {@code synchronized}. Concurrent appends
 * and reads from multiple {@code RealTaskRunner} instances sharing one
 * backend are safe.
 */
public final class BoundedWorkingMemory implements MemoryBackend {

    private static final double EXACT_SCORE = 1.0;
    private static final double SUBSTRING_SCORE = 0.8;

    private final int maxEntries;
    private final Deque<WorkingMemoryEntry> entries;

    public BoundedWorkingMemory(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException(
                    "maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        this.entries = new ArrayDeque<>(maxEntries);
    }

    /** Appends an entry with the current wall-clock time. */
    public synchronized void add(String role, String content) {
        add(role, content, Instant.now());
    }

    /** Appends an entry with an explicit timestamp (for tests). */
    public synchronized void add(String role, String content, Instant at) {
        WorkingMemoryEntry entry = new WorkingMemoryEntry(role, content, at);
        if (entries.size() >= maxEntries) {
            entries.removeFirst();
        }
        entries.addLast(entry);
    }

    /** Returns an immutable snapshot of the current entries, oldest first. */
    public synchronized List<WorkingMemoryEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }

    @Override
    public MemoryKind kind() {
        return MemoryKind.WORKING;
    }

    @Override
    public synchronized MemoryResult query(MemoryQuery query) {
        Objects.requireNonNull(query, "query");
        if (!query.kinds().contains(MemoryKind.WORKING)) {
            return new MemoryResult(List.of(), java.util.Set.of());
        }
        String needle = query.text();
        String needleLower = needle.toLowerCase(Locale.ROOT);

        List<MemoryHit> hits = new ArrayList<>();
        // Iterate oldest-to-newest so fusion (S4.4) sees stable order.
        for (WorkingMemoryEntry entry : entries) {
            if (hits.size() >= query.maxHits()) break;
            double score = scoreOf(entry.content(), needle, needleLower);
            if (score > 0) {
                hits.add(new MemoryHit(MemoryKind.WORKING,
                        "working:" + entry.role(),
                        entry.content(),
                        score));
            }
        }
        return new MemoryResult(hits, java.util.Set.of(MemoryKind.WORKING));
    }

    private double scoreOf(String content, String needle, String needleLower) {
        if (content.contains(needle)) return EXACT_SCORE;
        if (content.toLowerCase(Locale.ROOT).contains(needleLower)) {
            return SUBSTRING_SCORE;
        }
        return 0.0;
    }
}
```

### 4.4 Update `engine-memory/package-info.java`

Replace the existing content with:

```java
/**
 * Memory retrieval and compaction for the Apex kernel. Tier 0.
 * Defines the router, the backend contract, hit types, and the
 * context compactor contract. Implementations: BoundedWorkingMemory
 * (S4.1), episodic (S4.2), semantic (S4.3), router fusion (S4.4),
 * compactor (S4.5).
 */
@NullMarked
package com.apex.engine.memory;

import org.jspecify.annotations.NullMarked;
```

### 4.5 Update `engine-memory/README.md`

Replace the "## Types" and "## Tests" sections with:

```markdown
## Types
- `MemoryKind` — WORKING / EPISODIC / SEMANTIC
- `MemoryQuery` — retrieval request
- `MemoryHit` — single result
- `MemoryResult` — fused result set
- `MemoryBackend` — one backend, one kind
- `MemoryRouter` — multi-backend fusion service
- `ContextCompactor` — history compaction interface
- `WorkingMemoryEntry` — role, content, timestamp
- `BoundedWorkingMemory` — FIFO working memory backend (max entries)

## Tests
MemoryTypesTest · BoundedWorkingMemoryTest
```

## 5. Test file

### `engine-memory/src/test/java/com/apex/engine/memory/BoundedWorkingMemoryTest.java`

```java
package com.apex.engine.memory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BoundedWorkingMemoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void addAndSize() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "hello", T0);
        mem.add("assistant", "world", T0);
        assertThat(mem.size()).isEqualTo(2);
    }

    @Test
    void entriesAreReturnedOldestFirst() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "first", T0);
        mem.add("assistant", "second", T0);
        mem.add("assistant", "third", T0);
        assertThat(mem.entries())
                .extracting(WorkingMemoryEntry::content)
                .containsExactly("first", "second", "third");
    }

    @Test
    void evictionIsFifoAtCapacity() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(3);
        mem.add("assistant", "one", T0);
        mem.add("assistant", "two", T0);
        mem.add("assistant", "three", T0);
        mem.add("assistant", "four", T0);
        assertThat(mem.entries())
                .extracting(WorkingMemoryEntry::content)
                .containsExactly("two", "three", "four");
    }

    @Test
    void entriesReturnsImmutableSnapshot() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(3);
        mem.add("assistant", "x", T0);
        List<WorkingMemoryEntry> snapshot = mem.entries();
        assertThat(snapshot).hasSize(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> snapshot.add(new WorkingMemoryEntry(
                        "assistant", "y", T0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void clearEmptiesBuffer() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(3);
        mem.add("assistant", "x", T0);
        mem.clear();
        assertThat(mem.size()).isZero();
        assertThat(mem.entries()).isEmpty();
    }

    @Test
    void queryExactMatchScoresOne() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "the quick brown fox", T0);
        MemoryQuery q = new MemoryQuery(
                "quick", Set.of(MemoryKind.WORKING), 10);
        MemoryResult result = mem.query(q);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).score()).isEqualTo(1.0);
        assertThat(result.hits().get(0).kind())
                .isEqualTo(MemoryKind.WORKING);
        assertThat(result.queried()).containsExactly(MemoryKind.WORKING);
    }

    @Test
    void queryCaseInsensitiveScoresLower() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "The QUICK brown fox", T0);
        MemoryQuery q = new MemoryQuery(
                "quick", Set.of(MemoryKind.WORKING), 10);
        MemoryResult result = mem.query(q);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).score()).isEqualTo(0.8);
    }

    @Test
    void queryMissReturnsEmpty() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "alpha", T0);
        MemoryQuery q = new MemoryQuery(
                "zzz", Set.of(MemoryKind.WORKING), 10);
        assertThat(mem.query(q).hits()).isEmpty();
    }

    @Test
    void queryForOtherKindReturnsEmptyAndNoConsultation() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "quick", T0);
        MemoryQuery q = new MemoryQuery(
                "quick", Set.of(MemoryKind.SEMANTIC), 10);
        MemoryResult result = mem.query(q);
        assertThat(result.hits()).isEmpty();
        // Backend was not consulted.
        assertThat(result.queried()).isEmpty();
    }

    @Test
    void queryRespectsMaxHits() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(10);
        for (int i = 0; i < 5; i++) {
            mem.add("assistant", "hit " + i, T0);
        }
        MemoryQuery q = new MemoryQuery(
                "hit", Set.of(MemoryKind.WORKING), 2);
        assertThat(mem.query(q).hits()).hasSize(2);
    }

    @Test
    void queryPreservesOldestFirstOrdering() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(10);
        mem.add("assistant", "first hit", T0);
        mem.add("assistant", "second hit", T0);
        mem.add("assistant", "third hit", T0);
        MemoryQuery q = new MemoryQuery(
                "hit", Set.of(MemoryKind.WORKING), 10);
        assertThat(mem.query(q).hits())
                .extracting(MemoryHit::content)
                .containsExactly("first hit", "second hit", "third hit");
    }

    @Test
    void zeroCapacityRejected() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new BoundedWorkingMemory(0));
    }

    @Test
    void negativeCapacityRejected() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new BoundedWorkingMemory(-1));
    }

    @Test
    void addRejectsBlankRole() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(3);
        assertThatIllegalArgumentException().isThrownBy(
                () -> mem.add("", "content", T0));
    }

    @Test
    void kindIsWorking() {
        assertThat(new BoundedWorkingMemory(3).kind())
                .isEqualTo(MemoryKind.WORKING);
    }
}
```

## 6. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `MemoryBackend` interface exists with `kind()` and
      `query(MemoryQuery)`.
- [ ] `WorkingMemoryEntry` record exists with `(role, content, at)`.
- [ ] `BoundedWorkingMemory` implements `MemoryBackend`,
      `kind() == WORKING`, all methods `synchronized`.
- [ ] `BoundedWorkingMemoryTest` passes all 15 tests.
- [ ] `MemoryTypesTest` (S4.0) still passes.
- [ ] `engine-memory/package-info.java` and `README.md` updated.
- [ ] Enforcer passes on `engine-memory`; `jdeps` gate (Tier 0 only)
      still passes.
- [ ] No file exceeds 200 lines.
- [ ] No enterprise dependency added.
- [ ] `docs/INVARIANTS.md` unchanged.
- [ ] `engine-sandbox` untouched.
- [ ] No spec files read or created.

## 7. Do NOT add

- Any implementation of `MemoryRouter`, `ContextCompactor`, or any
  other `MemoryBackend`
- Any persistent storage (working memory is in-memory only)
- Any embeddings, tokenizers, or scoring beyond the tiered
  exact/substring scheme
- Any LRU or weighted eviction
- Any change to `MemoryQuery`, `MemoryHit`, `MemoryResult`,
  `MemoryRouter`, `ContextCompactor`, or `MemoryKind`
- Any change to `engine-sandbox` or any other module
- Any Spring, Jackson, Micrometer, Google, or Embabel dependency
- Any spec file
- Any file not listed in §4 or §5

## 8. Commit

Three commits on `phase/s4.1-working-memory`:

1. `feat(S4.1): add MemoryBackend interface`
2. `feat(S4.1): add WorkingMemoryEntry and BoundedWorkingMemory`
3. `test(S4.1): add working memory tests; update package-info and README`

Push. Report CI link, per-test-class pass counts, and confirm:
- `BoundedWorkingMemoryTest.evictionIsFifoAtCapacity` passed
- `BoundedWorkingMemoryTest.queryCaseInsensitiveScoresLower` passed
- `BoundedWorkingMemoryTest.queryForOtherKindReturnsEmptyAndNoConsultation`
  passed
- `MemoryTypesTest` (S4.0) still passes

If CI fails twice on the same root cause, stop and report the log.

---

**End of S4.1 session package.**