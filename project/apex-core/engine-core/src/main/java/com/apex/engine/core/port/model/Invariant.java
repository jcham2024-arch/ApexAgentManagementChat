package com.apex.engine.core.port.model;

import java.util.List;
import java.util.Objects;

/**
 * A single, named invariant that a plan must satisfy before execution.
 * Invariants are compiled pre-execution; an {@code InvariantChecker}
 * rejects any plan that violates one, fail-closed. Immutable.
 */
public record Invariant(String id, String description) {

    public Invariant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        if (id.isBlank()) {
            throw new IllegalArgumentException("invariant id must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("invariant description must not be blank");
        }
    }
}
