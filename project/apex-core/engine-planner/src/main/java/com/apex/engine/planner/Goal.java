package com.apex.engine.planner;

import java.util.List;
import java.util.Objects;

/**
 * A target state expressed as required conditions. The planner stops
 * when all required conditions are satisfied.
 */
public record Goal(String id, List<Condition> required) {
    public Goal {
        Objects.requireNonNull(id, "id");
        required = List.copyOf(required);
        if (required.isEmpty()) {
            throw new IllegalArgumentException("goal needs at least one condition");
        }
    }

    public boolean satisfiedBy(Blackboard state) {
        return required.stream().allMatch(c -> c.evaluate(state));
    }

    public int unsatisfiedCount(Blackboard state) {
        return (int) required.stream().filter(c -> !c.evaluate(state)).count();
    }
}
