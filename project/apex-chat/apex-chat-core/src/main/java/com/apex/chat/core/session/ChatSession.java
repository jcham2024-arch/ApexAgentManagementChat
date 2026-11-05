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
 * Aggregate root for a chat session. Owns an ordered, append-only event
 * history and tenant context.
 *
 * <p>All mutations go through {@link #append(ChatLifecycleEvent)}, which
 * enforces monotonic sequence numbers and is {@code synchronized} for
 * thread safety. The session assumes a single writer per session
 * instance; concurrent sessions are isolated by session id.
 *
 * <p>Transitions: {@link SessionState#OPEN} → {@link SessionState#CLOSED}
 * or {@link SessionState#OPEN} → {@link SessionState#FAILED}. Once
 * {@code CLOSED} or {@code FAILED}, no further events may be appended.
 */
public final class ChatSession {

    private final ChatSessionId id;
    private final TenantId tenantId;
    private final List<ChatLifecycleEvent> events;
    private SessionState state;
    private long lastSequence;

    public ChatSession(ChatSessionId id, TenantId tenantId,
                        List<ChatLifecycleEvent> events,
                        SessionState state, long lastSequence) {
        this.id = id;
        this.tenantId = tenantId;
        this.events = new ArrayList<>(events);
        this.state = state;
        this.lastSequence = lastSequence;
    }

    /**
     * Creates a new session in the {@link SessionState#OPEN} state with
     * no events.
     */
    public static ChatSession open(ChatSessionId id, TenantId tenantId) {
        return new ChatSession(id, tenantId, List.of(), SessionState.OPEN, 0);
    }

    public ChatSessionId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public SessionState state() {
        return state;
    }

    /**
     * Returns an immutable snapshot of all events, oldest first.
     */
    public List<ChatLifecycleEvent> events() {
        return List.copyOf(events);
    }

    public long lastSequence() {
        return lastSequence;
    }

    /**
     * Appends an event to the session. The event's sequence number must
     * be exactly one greater than the current {@link #lastSequence()}.
     * The session state transitions to {@link SessionState#CLOSED} or
     * {@link SessionState#FAILED} when the event type indicates closure
     * or failure.
     *
     * @throws IllegalArgumentException if the sequence is not monotonic
     *         or the session is already closed/failed
     */
    public synchronized void append(ChatLifecycleEvent event) {
        Objects.requireNonNull(event, "event");
        if (state != SessionState.OPEN) {
            throw new IllegalArgumentException(
                    "cannot append to session in state " + state);
        }
        if (event.sequence() != lastSequence + 1) {
            throw new IllegalArgumentException(
                "expected sequence " + (lastSequence + 1)
                + " but got " + event.sequence());
        }
        if (!event.sessionId().equals(id)) {
            throw new IllegalArgumentException(
                "event sessionId " + event.sessionId()
                + " does not match session id " + id);
        }
        if (!event.tenantId().equals(tenantId)) {
            throw new IllegalArgumentException(
                "event tenantId " + event.tenantId()
                + " does not match session tenantId " + tenantId);
        }

        events.add(event);
        lastSequence = event.sequence();

        switch (event.type()) {
            case SESSION_CLOSED -> state = SessionState.CLOSED;
            case SESSION_FAILED -> state = SessionState.FAILED;
            default -> {}
        }
    }

    /**
     * Takes an immutable snapshot of the current session state.
     */
    public ChatSessionSnapshot snapshot() {
        return new ChatSessionSnapshot(
            id, tenantId, state, events(), lastSequence,
            java.time.Clock.systemUTC().instant()
        );
    }
}
