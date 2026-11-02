package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * One recorded step of a session. {@code index} is the step's position
 * within the session; {@code durationMs} measures the step.
 */
public record StepLogEntry(String stepId, String sessionId, int index,
                           String stepType, long durationMs) {
    public StepLogEntry {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(stepType, "stepType");
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be >= 0");
        }
    }
}
