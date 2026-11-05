package com.apex.chat.core.event;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import java.time.Instant;
import java.util.Objects;

/**
 * Emitted when a planner step completes successfully.
 */
public record PlanStepCompleted(
    ChatSessionId sessionId,
    TenantId tenantId,
    Instant occurredAt,
    long sequence,
    String stepId,
    long durationMs
) implements ChatLifecycleEvent {
    public PlanStepCompleted {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(stepId, "stepId");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be >= 0");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs must be >= 0");
    }

    @Override
    public ChatEventType type() {
        return ChatEventType.PLAN_STEP_COMPLETED;
    }
}
