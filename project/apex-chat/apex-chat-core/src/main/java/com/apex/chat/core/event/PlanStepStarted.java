package com.apex.chat.core.event;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import java.time.Instant;
import java.util.Objects;

/**
 * Emitted when a planner step begins execution.
 */
public record PlanStepStarted(
    ChatSessionId sessionId,
    TenantId tenantId,
    Instant occurredAt,
    long sequence,
    String stepId,
    String actionId
) implements ChatLifecycleEvent {
    public PlanStepStarted {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(actionId, "actionId");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be >= 0");
    }

    @Override
    public ChatEventType type() {
        return ChatEventType.PLAN_STEP_STARTED;
    }
}
