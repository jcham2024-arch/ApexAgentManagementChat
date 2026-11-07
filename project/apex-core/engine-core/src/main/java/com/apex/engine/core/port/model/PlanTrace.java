package com.apex.engine.core.port.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A trace of plan execution steps, used by evaluation probes. Tier 0.
 */
public record PlanTrace(
        String sessionId,
        String manifestId,
        List<StepTrace> steps,
        Double finalScore
) {
    public PlanTrace {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(manifestId, "manifestId");
        Objects.requireNonNull(steps, "steps");
        steps = List.copyOf(steps);
    }

    /** A single step trace within a plan trace. */
    public record StepTrace(
            int stepIndex,
            String actionId,
            String toolName,
            String input,
            String output,
            boolean isError,
            long durationMs
    ) {
        public StepTrace {
            Objects.requireNonNull(actionId, "actionId");
            Objects.requireNonNull(toolName, "toolName");
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(output, "output");
        }
    }
}
