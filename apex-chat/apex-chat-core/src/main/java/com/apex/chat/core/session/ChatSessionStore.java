package com.apex.chat.core.session;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import java.util.List;
import java.util.Objects;

/**
 * Port for persisting and loading chat sessions. Tier 0.
 * Implementations are responsible for tenant isolation: a query for
 * tenant A must never return sessions written by tenant B.
 */
public interface ChatSessionStore {

    /**
     * Persists a session. Implementations must be idempotent: saving the
     * same session twice must not duplicate data.
     */
    void save(ChatSession session);

    /**
     * Loads a session by ID. Returns {@link java.util.Optional#empty()}
     * if no session exists with the given ID.
     */
    java.util.Optional<ChatSession> load(ChatSessionId id);

    /**
     * Returns all session IDs for the given tenant. The list may be
     * empty if the tenant has no sessions.
     */
    List<ChatSessionId> listByTenant(TenantId tenantId);
}
