package com.apex.chat.core.telemetry;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.event.ChatEventType;
import com.apex.chat.core.event.ChatLifecycleEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * A telemetry event emitted during a chat session. Carries generation
 * telemetry and cost estimation alongside the standard lifecycle event
 * fields. Implements {@link ChatLifecycleEvent} with
 * {@link ChatEventType#TELEMETRY_RECORDED}.
 */
public record TelemetryEvent(
    ChatSessionId sessionId,
    TenantId tenantId,
    long sequence,
    Instant occurredAt,
    GenerationTelemetry generation,
    CostEstimation cost
) implements ChatLifecycleEvent {
    public TelemetryEvent {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(cost, "cost");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be >= 0");
    }

    @Override
    public ChatEventType type() {
        return ChatEventType.TELEMETRY_RECORDED;
    }
}
