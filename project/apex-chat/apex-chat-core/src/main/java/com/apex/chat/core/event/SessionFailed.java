package com.apex.chat.core.event;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import java.time.Instant;
import java.util.Objects;

/**
 * Emitted when a chat session fails.
 */
public record SessionFailed(
    ChatSessionId sessionId,
    TenantId tenantId,
    Instant occurredAt,
    long sequence,
    String failureReason
) implements ChatLifecycleEvent {
    public SessionFailed {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(failureReason, "failureReason");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be >= 0");
    }

    @Override
    public ChatEventType type() {
        return ChatEventType.SESSION_FAILED;
    }
}
