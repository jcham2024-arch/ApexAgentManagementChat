package com.apex.engine.planner;

import java.util.List;
import java.util.Objects;

/**
 * An ordered sequence of actions whose combined effects satisfy a
 * goal. Cost is the sum of base costs plus risk penalties. Depth is
 * the number of steps in the plan.
 */
public record Plan(List<Action> steps, double cost, int depth) {
    public Plan {
        Objects.requireNonNull(steps, "steps");
        steps = List.copyOf(steps);
        if (cost < 0) throw new IllegalArgumentException("cost < 0");
        if (depth < 0) throw new IllegalArgumentException("depth < 0");
    }
}
