package com.apex.engine.memory;

import java.util.Objects;
import java.util.Set;

/**
 * Retrieval request to the memory subsystem. {@code text} is the query
 * string. {@code kinds} names which backends to consult; empty means
 * none (rejected). {@code maxHits} caps the total results returned
 * across all consulted backends.
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
