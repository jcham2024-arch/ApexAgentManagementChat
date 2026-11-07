package com.apex.engine.memory;

import java.util.Objects;

/**
 * A single result from a memory retrieval. {@code score} is a
 * relevance score in [0, infinity); higher is better. Fusion across
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
