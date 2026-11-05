package com.apex.chat.core.session;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.SessionState;
import com.apex.chat.core.event.ApprovalRequested;
import com.apex.chat.core.event.ChatLifecycleEvent;
import com.apex.chat.core.event.ChatEventType;
import com.apex.chat.core.event.PlanStepStarted;
import com.apex.chat.core.event.SessionOpened;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatSessionTest {

    private static final ChatSessionId SESSION_ID = new ChatSessionId("s-1");
    private static final TenantId TENANT_ID = new TenantId("t-1");
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createsOpenSession() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);

        assertThat(session.id()).isEqualTo(SESSION_ID);
        assertThat(session.tenantId()).isEqualTo(TENANT_ID);
        assertThat(session.state()).isEqualTo(SessionState.OPEN);
        assertThat(session.events()).isEmpty();
        assertThat(session.lastSequence()).isZero();
    }

    @Test
    void appendsEventsInOrder() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);

        session.append(new SessionOpened(SESSION_ID, TENANT_ID, TIME, 1));
        session.append(new PlanStepStarted(SESSION_ID, TENANT_ID, TIME, 2, "step-1", "action-1"));

        assertThat(session.lastSequence()).isEqualTo(2);
        assertThat(session.events()).hasSize(2);
        assertThat(session.events().get(0).type()).isEqualTo(ChatEventType.SESSION_OPENED);
        assertThat(session.events().get(1).type()).isEqualTo(ChatEventType.PLAN_STEP_STARTED);
    }

    @Test
    void rejectsNonMonotonicSequence() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(new SessionOpened(SESSION_ID, TENANT_ID, TIME, 1));

        assertThatThrownBy(() -> session.append(
                new SessionOpened(SESSION_ID, TENANT_ID, TIME, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSessionClosed() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(new SessionOpened(SESSION_ID, TENANT_ID, TIME, 1));
        session.append(new com.apex.chat.core.event.SessionClosed(SESSION_ID, TENANT_ID, TIME, 2));

        assertThatThrownBy(() -> session.append(
                new PlanStepStarted(SESSION_ID, TENANT_ID, TIME, 3, "step-1", "action-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSessionFailed() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(new SessionOpened(SESSION_ID, TENANT_ID, TIME, 1));
        session.append(new com.apex.chat.core.event.SessionFailed(SESSION_ID, TENANT_ID, TIME, 2, "error"));

        assertThatThrownBy(() -> session.append(
                new PlanStepStarted(SESSION_ID, TENANT_ID, TIME, 3, "step-1", "action-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWrongSessionId() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        ChatSessionId otherId = new ChatSessionId("other");

        assertThatThrownBy(() -> session.append(
                new SessionOpened(otherId, TENANT_ID, TIME, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWrongTenantId() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        TenantId otherTenant = new TenantId("other");

        assertThatThrownBy(() -> session.append(
                new SessionOpened(SESSION_ID, otherTenant, TIME, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsImmutableSnapshot() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(new SessionOpened(SESSION_ID, TENANT_ID, TIME, 1));

        java.util.List<com.apex.chat.core.event.ChatLifecycleEvent> events = session.events();
        assertThatThrownBy(() -> events.add(null))
                .isInstanceOf(java.lang.UnsupportedOperationException.class);
    }

    @Test
    void snapshotCapturesState() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(new SessionOpened(SESSION_ID, TENANT_ID, TIME, 1));

        com.apex.chat.core.session.ChatSessionSnapshot snapshot = session.snapshot();

        assertThat(snapshot.id()).isEqualTo(SESSION_ID);
        assertThat(snapshot.tenantId()).isEqualTo(TENANT_ID);
        assertThat(snapshot.state()).isEqualTo(SessionState.OPEN);
        assertThat(snapshot.events()).hasSize(1);
        assertThat(snapshot.lastSequence()).isEqualTo(1);
    }
}
