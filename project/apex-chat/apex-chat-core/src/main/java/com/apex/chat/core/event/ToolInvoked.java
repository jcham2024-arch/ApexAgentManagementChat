package com.apex.chat.core.event;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import java.time.Instant;
import java.util.Objects;

/**
 * Emitted when a tool is invoked during a step.
 */
public record ToolInvoked(
    ChatSessionId sessionId,
    TenantId tenantId,
    Instant occurredAt,
    long sequence,
    String toolName,
    String argumentsJson
) implements ChatLifecycleEvent {
    public ToolInvoked {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(argumentsJson, "argumentsJson");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be >= 0");
    }

    @Override
    public ChatEventType type() {
        return ChatEventType.TOOL_INVOKED;
    }
}
