package com.apex.chat.core.session;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.event.SessionOpened;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatSessionStoreTest {

    private static final ChatSessionId SESSION_ID = new ChatSessionId("s-1");
    private static final TenantId TENANT_ID = new TenantId("t-1");
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void saveAndLoadSession() {
        InMemoryChatSessionStore store = new InMemoryChatSessionStore();

        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(new SessionOpened(SESSION_ID, TENANT_ID, TIME, 1));

        store.save(session);

        ChatSession loaded = store.load(SESSION_ID).orElseThrow();

        assertThat(loaded.id()).isEqualTo(SESSION_ID);
        assertThat(session.tenantId()).isEqualTo(TENANT_ID);
        assertThat(loaded.events()).hasSize(1);
        assertThat(loaded.lastSequence()).isEqualTo(1);
    }

    @Test
    void loadMissingSessionReturnsEmpty() {
        InMemoryChatSessionStore store = new InMemoryChatSessionStore();

        assertThat(store.load(new ChatSessionId("missing"))).isEmpty();
    }

    @Test
    void listByTenantReturnsTenantSessions() {
        InMemoryChatSessionStore store = new InMemoryChatSessionStore();

        ChatSession session1 = ChatSession.open(new ChatSessionId("s-1"), TENANT_ID);
        session1.append(new SessionOpened(new ChatSessionId("s-1"), TENANT_ID, TIME, 1));

        ChatSession session2 = ChatSession.open(new ChatSessionId("s-2"), TENANT_ID);
        session2.append(new SessionOpened(new ChatSessionId("s-2"), TENANT_ID, TIME, 1));

        ChatSession otherSession = ChatSession.open(new ChatSessionId("s-3"), new TenantId("other"));
        otherSession.append(new SessionOpened(new ChatSessionId("s-3"), new TenantId("other"), TIME, 1));

        store.save(session1);
        store.save(session2);
        store.save(otherSession);

        List<ChatSessionId> tenantSessions = store.listByTenant(TENANT_ID);

        assertThat(tenantSessions).hasSize(2);
        assertThat(tenantSessions).contains(new ChatSessionId("s-1"));
        assertThat(tenantSessions).contains(new ChatSessionId("s-2"));
        assertThat(tenantSessions).doesNotContain(new ChatSessionId("s-3"));
    }

    @Test
    void listByTenantEmptyForUnknownTenant() {
        InMemoryChatSessionStore store = new InMemoryChatSessionStore();

        assertThat(store.listByTenant(new TenantId("unknown"))).isEmpty();
    }

    @Test
    void rejectsNullSession() {
        InMemoryChatSessionStore store = new InMemoryChatSessionStore();

        assertThatThrownBy(() -> store.save(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSessionId() {
        InMemoryChatSessionStore store = new InMemoryChatSessionStore();

        assertThatThrownBy(() -> store.load(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullTenantId() {
        InMemoryChatSessionStore store = new InMemoryChatSessionStore();

        assertThatThrownBy(() -> store.listByTenant(null))
                .isInstanceOf(NullPointerException.class);
    }
}
