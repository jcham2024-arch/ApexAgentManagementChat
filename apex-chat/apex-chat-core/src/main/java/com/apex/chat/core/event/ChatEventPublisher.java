package com.apex.chat.core.event;

import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.event.ChatLifecycleEvent;
import java.util.function.Consumer;

/**
 * Default no-op implementation of {@link ChatEventRegistry}. Used when
 * no event sink is configured. Safe to use as a placeholder before the
 * real implementation is wired in.
 */
public final class ChatEventPublisher implements ChatEventRegistry {

    private ChatEventPublisher() {}

    private static final ChatEventPublisher INSTANCE = new ChatEventPublisher();

    public static ChatEventRegistry instance() {
        return INSTANCE;
    }

    @Override
    public void subscribe(ChatSessionId sessionId, Consumer<ChatLifecycleEvent> listener) {
        // no-op
    }

    @Override
    public void unsubscribe(ChatSessionId sessionId, Consumer<ChatLifecycleEvent> listener) {
        // no-op
    }

    @Override
    public void publish(ChatLifecycleEvent event) {
        // no-op
    }
}
