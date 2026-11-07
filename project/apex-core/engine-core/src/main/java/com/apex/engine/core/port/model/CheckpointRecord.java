package com.apex.engine.core.port.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A checkpoint snapshot taken at a step boundary during plan execution.
 * Captures the session, step index, and a serialized state snapshot so
 * that execution can be resumed deterministically after interruption.
 * Immutable. Tier 0.
 */
public record CheckpointRecord(
        String sessionId,
        int stepIndex,
        String stateSnapshot,
        Instant savedAt
) {
    public CheckpointRecord {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(stateSnapshot, "stateSnapshot");
        Objects.requireNonNull(savedAt, "savedAt");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (stepIndex < 0) {
            throw new IllegalArgumentException("stepIndex must not be negative");
        }
        if (stateSnapshot.isBlank()) {
            throw new IllegalArgumentException("stateSnapshot must not be blank");
        }
    }
}
