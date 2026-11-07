package com.apex.engine.core.loop;

import com.apex.engine.planner.Blackboard;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of a {@link PlanLoop} run.
 */
public record PlanLoopResult(PlanStatus status, Blackboard finalState,
                             List<StepRecord> steps) {
    public PlanLoopResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(finalState, "finalState");
        steps = List.copyOf(steps);
    }
}
