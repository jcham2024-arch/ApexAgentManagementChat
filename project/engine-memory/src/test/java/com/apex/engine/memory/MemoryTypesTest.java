package com.apex.engine.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemoryTypesTest {

    @Test
    void memoryQueryRejectsNullText() {
        assertThatThrownBy(() -> new MemoryQuery(null, Set.of(MemoryKind.WORKING), 5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void memoryQueryRejectsBlankText() {
        assertThatThrownBy(() -> new MemoryQuery("   ", Set.of(MemoryKind.WORKING), 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void memoryQueryRejectsEmptyKinds() {
        assertThatThrownBy(() -> new MemoryQuery("text", Set.of(), 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void memoryQueryRejectsNonPositiveMaxHits() {
        assertThatThrownBy(() -> new MemoryQuery("text", Set.of(MemoryKind.WORKING), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryQuery("text", Set.of(MemoryKind.WORKING), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void memoryQueryDefensivelyCopiesKinds() {
        java.util.Set<MemoryKind> kinds = new java.util.HashSet<>();
        kinds.add(MemoryKind.WORKING);
        MemoryQuery query = new MemoryQuery("text", kinds, 5);
        kinds.add(MemoryKind.EPISODIC);
        assertThat(query.kinds()).containsExactly(MemoryKind.WORKING);
        assertThatThrownBy(() -> query.kinds().add(MemoryKind.SEMANTIC))
                .isInstanceOf(java.lang.UnsupportedOperationException.class);
    }

    @Test
    void memoryHitRejectsNullKind() {
        assertThatThrownBy(() -> new MemoryHit(null, "source", "content", 1.0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void memoryHitRejectsNullSource() {
        assertThatThrownBy(() -> new MemoryHit(MemoryKind.WORKING, null, "content", 1.0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void memoryHitRejectsNullContent() {
        assertThatThrownBy(() -> new MemoryHit(MemoryKind.WORKING, "source", null, 1.0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void memoryHitRejectsNegativeScore() {
        assertThatThrownBy(() -> new MemoryHit(MemoryKind.WORKING, "source", "content", -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void memoryHitRejectsBlankSource() {
        assertThatThrownBy(() -> new MemoryHit(MemoryKind.WORKING, "   ", "content", 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void memoryResultDefensivelyCopiesHitsAndQueried() {
        java.util.List<MemoryHit> hits = new java.util.ArrayList<>();
        hits.add(new MemoryHit(MemoryKind.WORKING, "s", "c", 1.0));
        java.util.Set<MemoryKind> queried = new java.util.HashSet<>();
        queried.add(MemoryKind.WORKING);

        MemoryResult result = new MemoryResult(hits, queried);
        hits.clear();
        queried.clear();

        assertThat(result.hits()).hasSize(1);
        assertThat(result.queried()).containsExactly(MemoryKind.WORKING);
    }
}
