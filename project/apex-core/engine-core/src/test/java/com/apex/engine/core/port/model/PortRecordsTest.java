package com.apex.engine.core.port.model;

import org.junit.jupiter.api.Test;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.planner.RiskLevel;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Non-null rejection for every port record component, one test per
 * record. Constructor guards are its only behavior.
 */
class PortRecordsTest {

    private static final TenantId TENANT = new TenantId("t-test");

    @Test
    void chatRequestRejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ChatRequest(null, List.of("hi")));
        assertThatNullPointerException()
                .isThrownBy(() -> new ChatRequest("m", null));
    }

    @Test
    void chatResponseRejectsNullContent() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ChatResponse(null));
    }

    @Test
    void planRequestRejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PlanRequest(null, List.of("read")));
        assertThatNullPointerException()
                .isThrownBy(() -> new PlanRequest("goal", null));
    }

    @Test
    void planResultRejectsNullList() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PlanResult(null));
    }

    @Test
    void toolCallRejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ToolCall(null, "read", "{}"));
        assertThatNullPointerException()
                .isThrownBy(() -> new ToolCall("c1", null, "{}"));
        assertThatNullPointerException()
                .isThrownBy(() -> new ToolCall("c1", "read", null));
    }

    @Test
    void toolResultRejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ToolResult(null, false, "out", 0L));
        assertThatNullPointerException()
                .isThrownBy(() -> new ToolResult("c1", false, null, 0L));
    }

    @Test
    void approvalRequestRejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ApprovalRequest(null, "s", RiskLevel.L0));
        assertThatNullPointerException()
                .isThrownBy(() -> new ApprovalRequest("r1", null, RiskLevel.L0));
        assertThatNullPointerException()
                .isThrownBy(() -> new ApprovalRequest("r1", "s", null));
    }

    @Test
    void approvalRequestRejectsBlankId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ApprovalRequest("  ", "s", RiskLevel.L0))
                .withMessage("id must not be blank");
    }

    @Test
    void approvalRequestRiskLevelIsTypedAndGatesApproval() {
        assertThat(new ApprovalRequest("r1", "s", RiskLevel.L2).riskLevel()
                .requiresApproval()).isFalse();
        assertThat(new ApprovalRequest("r2", "s", RiskLevel.L3).riskLevel()
                .requiresApproval()).isTrue();
    }

    @Test
    void approvalDecisionRejectsNullRequestId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ApprovalDecision(null, true, "ok"));
    }

    @Test
    void stepLogEntryRejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StepLogEntry(null, TENANT, "s", 0,
                        "plan", 0L));
        assertThatNullPointerException()
                .isThrownBy(() -> new StepLogEntry("s1", TENANT, null, 0,
                        "plan", 0L));
        assertThatNullPointerException()
                .isThrownBy(() -> new StepLogEntry("s1", TENANT, "s", 0, null,
                        0L));
    }

    @Test
    void stepLogEntryRejectsNullTenant() {
        assertThatNullPointerException().isThrownBy(() ->
                new StepLogEntry("id", null, "session", 0, "type", 0L));
    }

    @Test
    void vectorHitRejectsNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new VectorHit(null, 1.0d));
    }

    @Test
    void secretRefRejectsNullName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SecretRef(null));
    }

    @Test
    void auditEntryRejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AuditEntry(null, "login", Instant.EPOCH,
                        "", "h"));
        assertThatNullPointerException()
                .isThrownBy(() -> new AuditEntry("a1", null, Instant.EPOCH,
                        "", "h"));
        assertThatNullPointerException()
                .isThrownBy(() -> new AuditEntry("a1", "login", null, "", "h"));
        assertThatNullPointerException()
                .isThrownBy(() -> new AuditEntry("a1", "login", Instant.EPOCH,
                        "", null));
    }

    @Test
    void auditEntryRejectsNullPreviousHash() {
        assertThatNullPointerException().isThrownBy(() ->
                new AuditEntry("id", "action", Instant.now(), null, "hash"));
    }

    @Test
    void telemetrySpanRejectsNullName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TelemetrySpan(null, 0L));
    }
}
