# PHASE-S4.1-WORKING-MEMORY.md

**Version:** 1.0 · **Sub-phase of S4** · **Ends with a bounded working memory backend proven by tests.**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S4-MEMORY-SANDBOX.md` (repo — router)
4. `PHASE-S4.0-MEMORY-SANDBOX-BOOTSTRAP_1.md` (S4.0 bootstrap)
5. **This file**
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