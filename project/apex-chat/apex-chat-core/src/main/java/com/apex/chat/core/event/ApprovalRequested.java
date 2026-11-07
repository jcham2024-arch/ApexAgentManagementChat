package com.apex.chat.core.event;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import java.time.Instant;
import java.util.Objects;

/**
 * Emitted when an approval is requested for a tool call.
 */
public record ApprovalRequested(
    ChatSessionId sessionId,
    TenantId tenantId,
    Instant occurredAt,
    long sequence,
    String requestId,
    String toolName,
    String riskLevel
) implements ChatLifecycleEvent {
    public ApprovalRequested {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(riskLevel, "riskLevel");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be >= 0");
    }

    @Override
    public ChatEventType type() {
        return ChatEventType.APPROVAL_REQUESTED;
    }
}
