package com.apex.engine.core.port.model;

import java.util.List;
import java.util.Objects;

/**
 * A closed set of {@link Invariant}s carried by a plan. The set is
 * defensively copied so callers cannot mutate it after construction.
 * Immutable.
 */
public record InvariantSet(List<Invariant> invariants) {

    public InvariantSet {
        Objects.requireNonNull(invariants, "invariants");
        invariants = List.copyOf(invariants);
        for (Invariant invariant : invariants) {
            Objects.requireNonNull(invariant, "invariant element must not be null");
        }
    }

    /** Returns true when no invariants must be satisfied. */
    public boolean isEmpty() {
        return invariants.isEmpty();
    }

    /** Returns the number of invariants in this set. */
    public int size() {
        return invariants.size();
    }

    /** An empty invariant set (no constraints). */
    public static InvariantSet empty() {
        return new InvariantSet(List.of());
    }
}
