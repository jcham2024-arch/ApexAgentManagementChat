package com.apex.chat.core.session;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.event.ChatLifecycleEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ChatSessionStore}. Thread-safe for
 * concurrent reads and writes. Sessions are isolated by tenant ID:
 * {@link #listByTenant(TenantId)} only returns sessions for that tenant.
 *
 * <p>Not persisted across process restarts; file-backed persistence
 * lands in Tier 2 ({@code apex-chat-store-sqlite}).
 */
public final class InMemoryChatSessionStore implements ChatSessionStore {

    private final Map<ChatSessionId, ChatSession> sessions = new ConcurrentHashMap<>();
    private final Map<TenantId, List<ChatSessionId>> tenantIndex = new ConcurrentHashMap<>();

    @Override
    public void save(ChatSession session) {
        Objects.requireNonNull(session, "session");
        ChatSessionId id = session.id();
        TenantId tenant = session.tenantId();
        sessions.put(id, session);

        // Update tenant index
        tenantIndex.compute(tenant, (key, existing) -> {
            List<ChatSessionId> list = existing == null
                ? new ArrayList<>()
                : new ArrayList<>(existing);
            if (!list.contains(id)) {
                list.add(id);
            }
            return list;
        });
    }

    @Override
    public java.util.Optional<ChatSession> load(TenantId tenantId, ChatSessionId id) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(id, "id");
        ChatSession session = sessions.get(id);
        if (session == null || !session.tenantId().equals(tenantId)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(session);
    }

    @Override
    public List<ChatSessionId> listByTenant(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        List<ChatSessionId> ids = tenantIndex.get(tenantId);
        if (ids == null) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(ids));
    }

    /**
     * Returns the total number of sessions stored.
     */
    public int size() {
        return sessions.size();
    }

    /**
     * Removes all sessions. Primarily for testing.
     */
    public void clear() {
        sessions.clear();
        tenantIndex.clear();
    }
}
