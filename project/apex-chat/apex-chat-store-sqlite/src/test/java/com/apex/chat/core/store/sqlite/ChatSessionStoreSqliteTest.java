package com.apex.chat.core.store.sqlite;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.SessionState;
import com.apex.chat.core.event.ApprovalRequested;
import com.apex.chat.core.event.ApprovalResolved;
import com.apex.chat.core.event.ChatLifecycleEvent;
import com.apex.chat.core.event.PlanStepStarted;
import com.apex.chat.core.event.SessionClosed;
import com.apex.chat.core.event.SessionOpened;
import com.apex.chat.core.event.SessionFailed;
import com.apex.chat.core.event.ToolInvoked;
import com.apex.chat.core.session.ChatSession;
import com.apex.chat.core.session.ChatSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatSessionStoreSqliteTest {

    private static final ChatSessionId SESSION_ID = new ChatSessionId("s-1");
    private static final TenantId TENANT_ID = new TenantId("t-1");
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    @TempDir
    Path tempDir;

    Path dbPath;
    ChatSessionStore store;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test.db");
        store = new ChatSessionStoreSqlite(dbPath);
    }

    private static SessionOpened sessionOpened(ChatSessionId id, TenantId tenant, long seq) {
        return new SessionOpened(id, tenant, TIME, seq);
    }

    private static PlanStepStarted planStepStarted(ChatSessionId id, TenantId tenant, long seq,
                                                   String stepId, String actionId) {
        return new PlanStepStarted(id, tenant, TIME, seq, stepId, actionId);
    }

    private static ToolInvoked toolInvoked(ChatSessionId id, TenantId tenant, long seq,
                                           String toolName, String argsJson) {
        return new ToolInvoked(id, tenant, TIME, seq, toolName, argsJson);
    }

    private static ApprovalRequested approvalRequested(ChatSessionId id, TenantId tenant, long seq,
                                                       String requestId, String toolName,
                                                       String riskLevel) {
        return new ApprovalRequested(id, tenant, TIME, seq, requestId, toolName, riskLevel);
    }

    private static ApprovalResolved approvalResolved(ChatSessionId id, TenantId tenant, long seq,
                                                     String requestId, String toolName,
                                                     boolean approved) {
        return new ApprovalResolved(id, tenant, TIME, seq, requestId, toolName, approved);
    }

    private static SessionClosed sessionClosed(ChatSessionId id, TenantId tenant, long seq) {
        return new SessionClosed(id, tenant, TIME, seq);
    }

    private static SessionFailed sessionFailed(ChatSessionId id, TenantId tenant, long seq,
                                               String reason) {
        return new SessionFailed(id, tenant, TIME, seq, reason);
    }

    @Test
    void saveAndLoadSession() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(sessionOpened(SESSION_ID, TENANT_ID, 1));
        session.append(planStepStarted(SESSION_ID, TENANT_ID, 2, "step-1", "action-1"));
        session.append(toolInvoked(SESSION_ID, TENANT_ID, 3, "read_file", "{\"path\":\"/a.txt\"}"));
        session.append(planStepStarted(SESSION_ID, TENANT_ID, 4, "step-2", "action-2"));
        session.append(sessionClosed(SESSION_ID, TENANT_ID, 5));

        store.save(session);

        ChatSession loaded = store.load(TENANT_ID, SESSION_ID).orElseThrow();

        assertThat(loaded.id()).isEqualTo(SESSION_ID);
        assertThat(loaded.tenantId()).isEqualTo(TENANT_ID);
        assertThat(loaded.state()).isEqualTo(SessionState.CLOSED);
        assertThat(loaded.lastSequence()).isEqualTo(5);
        assertThat(loaded.events()).hasSize(5);

        // Verify event types in order
        List<ChatLifecycleEvent> events = loaded.events();
        assertThat(events.get(0).type()).isEqualTo(com.apex.chat.core.event.ChatEventType.SESSION_OPENED);
        assertThat(events.get(1).type()).isEqualTo(com.apex.chat.core.event.ChatEventType.PLAN_STEP_STARTED);
        assertThat(events.get(2).type()).isEqualTo(com.apex.chat.core.event.ChatEventType.TOOL_INVOKED);
        assertThat(events.get(3).type()).isEqualTo(com.apex.chat.core.event.ChatEventType.PLAN_STEP_STARTED);
        assertThat(events.get(4).type()).isEqualTo(com.apex.chat.core.event.ChatEventType.SESSION_CLOSED);
    }

    @Test
    void loadMissingSessionReturnsEmpty() {
        assertThat(store.load(TENANT_ID, new ChatSessionId("missing"))).isEmpty();
    }

    @Test
    void listByTenantReturnsTenantSessions() {
        ChatSession session1 = ChatSession.open(new ChatSessionId("s-1"), TENANT_ID);
        session1.append(sessionOpened(new ChatSessionId("s-1"), TENANT_ID, 1));

        ChatSession session2 = ChatSession.open(new ChatSessionId("s-2"), TENANT_ID);
        session2.append(sessionOpened(new ChatSessionId("s-2"), TENANT_ID, 1));

        ChatSession otherSession = ChatSession.open(new ChatSessionId("s-3"), new TenantId("other"));
        otherSession.append(sessionOpened(new ChatSessionId("s-3"), new TenantId("other"), 1));

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
        assertThat(store.listByTenant(new TenantId("unknown"))).isEmpty();
    }

    @Test
    void saveAndLoadWithApprovalEvents() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(sessionOpened(SESSION_ID, TENANT_ID, 1));
        session.append(approvalRequested(SESSION_ID, TENANT_ID, 2, "req-1", "run_command", "L4"));
        session.append(approvalResolved(SESSION_ID, TENANT_ID, 3, "req-1", "run_command", true));

        store.save(session);

        ChatSession loaded = store.load(TENANT_ID, SESSION_ID).orElseThrow();
        List<ChatLifecycleEvent> events = loaded.events();

        assertThat(events).hasSize(3);
        assertThat(events.get(0).type()).isEqualTo(com.apex.chat.core.event.ChatEventType.SESSION_OPENED);
        assertThat(events.get(1).type()).isEqualTo(com.apex.chat.core.event.ChatEventType.APPROVAL_REQUESTED);
        assertThat(events.get(2).type()).isEqualTo(com.apex.chat.core.event.ChatEventType.APPROVAL_RESOLVED);

        ApprovalResolved resolved = (ApprovalResolved) events.get(2);
        assertThat(resolved.approved()).isTrue();
        assertThat(resolved.requestId()).isEqualTo("req-1");
    }

    @Test
    void saveSessionFailed() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(sessionOpened(SESSION_ID, TENANT_ID, 1));
        session.append(sessionFailed(SESSION_ID, TENANT_ID, 2, "timeout"));

        store.save(session);

        ChatSession loaded = store.load(TENANT_ID, SESSION_ID).orElseThrow();
        assertThat(loaded.state()).isEqualTo(SessionState.FAILED);
        assertThat(loaded.lastSequence()).isEqualTo(2);

        SessionFailed failed = (SessionFailed) loaded.events().get(1);
        assertThat(failed.failureReason()).isEqualTo("timeout");
    }

    @Test
    void persistsAcrossNewStoreInstance() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(sessionOpened(SESSION_ID, TENANT_ID, 1));

        store.save(session);

        // Create a new store instance pointing to the same DB
        ChatSessionStore store2 = new ChatSessionStoreSqlite(dbPath);
        ChatSession loaded = store2.load(TENANT_ID, SESSION_ID).orElseThrow();

        assertThat(loaded.id()).isEqualTo(SESSION_ID);
        assertThat(loaded.lastSequence()).isEqualTo(1);
        assertThat(loaded.events()).hasSize(1);
    }

    @Test
    void rejectsNullSession() {
        assertThatThrownBy(() -> store.save(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSessionId() {
        assertThatThrownBy(() -> store.load(TENANT_ID, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullTenantIdOnLoad() {
        assertThatThrownBy(() -> store.load(null, SESSION_ID))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void loadOtherTenantReturnsEmpty() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(sessionOpened(SESSION_ID, TENANT_ID, 1));
        store.save(session);

        assertThat(store.load(new TenantId("other"), SESSION_ID)).isEmpty();
    }

    @Test
    void rejectsNullTenantId() {
        assertThatThrownBy(() -> store.listByTenant(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void updatesExistingSession() {
        ChatSession session = ChatSession.open(SESSION_ID, TENANT_ID);
        session.append(sessionOpened(SESSION_ID, TENANT_ID, 1));
        store.save(session);

        // Append more events and save again
        session.append(planStepStarted(SESSION_ID, TENANT_ID, 2, "step-1", "action-1"));
        store.save(session);

        ChatSession loaded = store.load(TENANT_ID, SESSION_ID).orElseThrow();
        assertThat(loaded.lastSequence()).isEqualTo(2);
        assertThat(loaded.events()).hasSize(2);
    }
}
