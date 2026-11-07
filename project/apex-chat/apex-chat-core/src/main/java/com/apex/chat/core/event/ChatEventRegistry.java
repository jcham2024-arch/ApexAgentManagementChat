package com.apex.chat.core.event;

import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.event.ChatLifecycleEvent;
import java.util.function.Consumer;

/**
 * Registry for subscribing to chat lifecycle events. Implementations
 * are responsible for delivery guarantees, ordering, and backpressure.
 * Tier 0.
 */
public interface ChatEventRegistry {

    /**
     * Subscribe a listener to events for a session. The listener is
     * invoked synchronously on the publishing thread unless the
     * implementation documents otherwise.
     */
    void subscribe(ChatSessionId sessionId, Consumer<ChatLifecycleEvent> listener);

    /**
     * Remove a previously registered listener.
     */
    void unsubscribe(ChatSessionId sessionId, Consumer<ChatLifecycleEvent> listener);

    /**
     * Publish an event to all subscribers of the session. Implementations
     * must be non-blocking or bounded; callers must not await listeners.
     * This preserves the K6 cancellation budget (&le;100 ms).
     */
    void publish(ChatLifecycleEvent event);
}
