package com.apex.engine.core.port;

import java.time.Instant;
import java.util.Objects;

/**
 * Sink for chat lifecycle events emitted by the engine kernel. Tier 0.
 * Implementations live in adapters (e.g. apex-chat-core). The no-op
 * default is used when no sink is configured.
 *
 * <p>Implementations must be non-blocking; callers must not await
 * listeners. This preserves the K6 cancellation budget (&le;100 ms).
 */
public interface ChatEventSink {

    /**
     * Emits an event. The implementation knows the session and tenant
     * context; this method receives only event-type-specific fields.
     *
     * @param eventType   the type of event (e.g. {@code "PLAN_STEP_STARTED"})
     * @param sequence    per-session monotonic sequence number
     * @param occurredAt  when the event occurred
     */
    void emit(String eventType, long sequence, Instant occurredAt);

    /** No-op sink that discards all events. */
    ChatEventSink NOOP = (eventType, sequence, occurredAt) -> {};
}
