package com.apex.chat.core.event;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import java.time.Instant;
import java.util.Objects;

/**
 * Emitted when a chat session is opened.
 */
public record SessionOpened(
    ChatSessionId sessionId,
    TenantId tenantId,
    Instant occurredAt,
    long sequence
) implements ChatLifecycleEvent {
    public SessionOpened {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be >= 0");
    }

    @Override
    public ChatEventType type() {
        return ChatEventType.SESSION_OPENED;
    }
}
