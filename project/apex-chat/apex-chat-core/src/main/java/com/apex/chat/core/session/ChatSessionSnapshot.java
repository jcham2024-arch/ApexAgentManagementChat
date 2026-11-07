package com.apex.chat.core.session;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.SessionState;
import com.apex.chat.core.event.ChatLifecycleEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot of a chat session at a point in time. Used for replay and
 * debugging. Immutable.
 */
public record ChatSessionSnapshot(
    ChatSessionId id,
    TenantId tenantId,
    SessionState state,
    List<ChatLifecycleEvent> events,
    long lastSequence,
    Instant snapshottedAt
) {
    public ChatSessionSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(events, "events");
        events = List.copyOf(events);
        if (lastSequence < 0) throw new IllegalArgumentException("lastSequence must be >= 0");
        Objects.requireNonNull(snapshottedAt, "snapshottedAt");
    }
}
