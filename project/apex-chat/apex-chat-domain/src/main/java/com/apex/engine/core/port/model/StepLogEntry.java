package com.apex.engine.core.port.model;

import com.apex.chat.common.identity.TenantId;

import java.util.Objects;

/**
 * A single step record. {@code tenantId} is required and must match
 * the tenant of any store it is appended to. {@code sessionId} is a
 * raw string for now (assigned by the CLI as a UUID); typed session
 * IDs land in S6.
 */
public record StepLogEntry(
        String stepId,
        TenantId tenantId,
        String sessionId,
        int index,
        String stepType,
        long durationMs
) {
    public StepLogEntry {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(stepType, "stepType");
        if (index < 0) throw new IllegalArgumentException("index < 0");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs < 0");
        if (stepId.isBlank()) {
            throw new IllegalArgumentException("stepId must not be blank");
        }
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (stepType.isBlank()) {
            throw new IllegalArgumentException("stepType must not be blank");
        }
    }
}
