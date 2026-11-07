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
