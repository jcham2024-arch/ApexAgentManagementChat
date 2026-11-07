package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * The outcome of routing one tool call through the {@link
 * ToolGatewayPort}. Three outcomes: the call is approved to proceed,
 * it is denied with a reason, or it requires human approval before
 * execution. Tier 0.
 */
public sealed interface GatewayDecision
        permits GatewayDecision.Approve, GatewayDecision.Deny,
        GatewayDecision.RequireApproval {

    /** The call was approved by policy; execution may proceed. */
    record Approve() implements GatewayDecision {}

    /** The call was denied by policy; reason explains why. */
    record Deny(String reason) implements GatewayDecision {
        public Deny {
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }
    }

    /**
     * The call requires human approval before execution. Carries the
     * {@link ApprovalRequest} to present to an approver.
     */
    record RequireApproval(ApprovalRequest request) implements GatewayDecision {
        public RequireApproval {
            Objects.requireNonNull(request, "request");
        }
    }
}
