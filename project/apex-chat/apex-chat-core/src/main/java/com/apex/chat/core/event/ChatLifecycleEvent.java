package com.apex.chat.core.event;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import java.time.Instant;
import java.util.Objects;

/**
 * A lifecycle event emitted during a chat session. Events are immutable
 * records.
 *
 * <p>{@link #sequence()} is a per-session monotonic counter, guaranteeing
 * deterministic replay.
 */
public interface ChatLifecycleEvent {

    ChatSessionId sessionId();

    ChatEventType type();

    Instant occurredAt();

    long sequence();

    TenantId tenantId();
}
