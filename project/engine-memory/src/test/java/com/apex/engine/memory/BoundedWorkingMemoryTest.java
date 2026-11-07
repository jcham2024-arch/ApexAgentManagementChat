package com.apex.engine.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
        assertThatThrownBy(() -> snapshot.add(new WorkingMemoryEntry(
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
        mem.add("assistant", "the quick brown fox", T0);
        MemoryQuery q = new MemoryQuery(
                "zebra", Set.of(MemoryKind.WORKING), 10);
        MemoryResult result = mem.query(q);
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void queryRespectsMaxHits() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "quick brown fox", T0);
        mem.add("assistant", "quick brown dog", T0);
        mem.add("assistant", "quick brown cat", T0);
        MemoryQuery q = new MemoryQuery("quick", Set.of(MemoryKind.WORKING), 2);
        MemoryResult result = mem.query(q);
        assertThat(result.hits()).hasSize(2);
    }

    @Test
    void queryForOtherKindReturnsEmpty() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "quick brown fox", T0);
        MemoryQuery q = new MemoryQuery(
                "quick", Set.of(MemoryKind.EPISODIC), 10);
        MemoryResult result = mem.query(q);
        assertThat(result.hits()).isEmpty();
        assertThat(result.queried()).isEmpty();
    }

    @Test
    void addRejectsNullRole() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        assertThatThrownBy(() -> mem.add(null, "content", T0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addRejectsBlankRole() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        assertThatThrownBy(() -> mem.add("   ", "content", T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addRejectsNullContent() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        assertThatThrownBy(() -> mem.add("assistant", null, T0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addRejectsNullAt() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        assertThatThrownBy(() -> mem.add("assistant", "content", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sizeMatchesEntriesCount() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        assertThat(mem.size()).isZero();
        mem.add("assistant", "one", T0);
        assertThat(mem.size()).isEqualTo(1);
        mem.add("assistant", "two", T0);
        assertThat(mem.size()).isEqualTo(2);
    }
}
