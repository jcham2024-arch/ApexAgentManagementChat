package com.apex.chat.core.event;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatLifecycleEventTest {

    private static final ChatSessionId SESSION_ID = new ChatSessionId("s-1");
    private static final TenantId TENANT_ID = new TenantId("t-1");
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void sessionOpenedEvent() {
        SessionOpened event = new SessionOpened(SESSION_ID, TENANT_ID, TIME, 0);

        assertThat(event.sessionId()).isEqualTo(SESSION_ID);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.occurredAt()).isEqualTo(TIME);
        assertThat(event.sequence()).isEqualTo(0);
        assertThat(event.type()).isEqualTo(ChatEventType.SESSION_OPENED);
    }

    @Test
    void planStepStartedEvent() {
        PlanStepStarted event = new PlanStepStarted(
                SESSION_ID, TENANT_ID, TIME, 1, "step-1", "action-1");

        assertThat(event.sessionId()).isEqualTo(SESSION_ID);
        assertThat(event.type()).isEqualTo(ChatEventType.PLAN_STEP_STARTED);
        assertThat(event.stepId()).isEqualTo("step-1");
        assertThat(event.actionId()).isEqualTo("action-1");
    }

    @Test
    void planStepCompletedEvent() {
        PlanStepCompleted event = new PlanStepCompleted(
                SESSION_ID, TENANT_ID, TIME, 2, "step-1", 42L);

        assertThat(event.sessionId()).isEqualTo(SESSION_ID);
        assertThat(event.type()).isEqualTo(ChatEventType.PLAN_STEP_COMPLETED);
        assertThat(event.stepId()).isEqualTo("step-1");
        assertThat(event.durationMs()).isEqualTo(42L);
    }

    @Test
    void toolInvokedEvent() {
        ToolInvoked event = new ToolInvoked(
                SESSION_ID, TENANT_ID, TIME, 3, "read_file",
                "{\"path\":\"/tmp/test.txt\"}");

        assertThat(event.sessionId()).isEqualTo(SESSION_ID);
        assertThat(event.type()).isEqualTo(ChatEventType.TOOL_INVOKED);
        assertThat(event.toolName()).isEqualTo("read_file");
        assertThat(event.argumentsJson()).isEqualTo("{\"path\":\"/tmp/test.txt\"}");
    }

    @Test
    void approvalRequestedEvent() {
        ApprovalRequested event = new ApprovalRequested(
                SESSION_ID, TENANT_ID, TIME, 4, "req-1", "run_command", "L4");

        assertThat(event.sessionId()).isEqualTo(SESSION_ID);
        assertThat(event.type()).isEqualTo(ChatEventType.APPROVAL_REQUESTED);
        assertThat(event.requestId()).isEqualTo("req-1");
        assertThat(event.toolName()).isEqualTo("run_command");
        assertThat(event.riskLevel()).isEqualTo("L4");
    }

    @Test
    void approvalResolvedEvent() {
        ApprovalResolved event = new ApprovalResolved(
                SESSION_ID, TENANT_ID, TIME, 5, "req-1", "run_command", true);

        assertThat(event.sessionId()).isEqualTo(SESSION_ID);
        assertThat(event.type()).isEqualTo(ChatEventType.APPROVAL_RESOLVED);
        assertThat(event.requestId()).isEqualTo("req-1");
        assertThat(event.toolName()).isEqualTo("run_command");
        assertThat(event.approved()).isTrue();
    }

    @Test
    void sessionClosedEvent() {
        SessionClosed event = new SessionClosed(
                SESSION_ID, TENANT_ID, TIME, 6);

        assertThat(event.sessionId()).isEqualTo(SESSION_ID);
        assertThat(event.type()).isEqualTo(ChatEventType.SESSION_CLOSED);
    }

    @Test
    void sessionFailedEvent() {
        SessionFailed event = new SessionFailed(
                SESSION_ID, TENANT_ID, TIME, 7, "timeout");

        assertThat(event.sessionId()).isEqualTo(SESSION_ID);
        assertThat(event.type()).isEqualTo(ChatEventType.SESSION_FAILED);
        assertThat(event.failureReason()).isEqualTo("timeout");
    }

    @Test
    void rejectsNullSessionId() {
        assertThatThrownBy(() -> new SessionOpened(
                null, TENANT_ID, TIME, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankSessionId() {
        assertThatThrownBy(() -> new SessionOpened(
                new ChatSessionId(""), TENANT_ID, TIME, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeSequence() {
        assertThatThrownBy(() -> new SessionOpened(
                SESSION_ID, TENANT_ID, TIME, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
