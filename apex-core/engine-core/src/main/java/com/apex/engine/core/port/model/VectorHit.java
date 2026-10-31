package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * One semantic search hit. {@code score} is the similarity the store
 * reported; the core does not rescale it.
 */
public record VectorHit(String id, double score) {
    public VectorHit {
        Objects.requireNonNull(id, "id");
    }
}
