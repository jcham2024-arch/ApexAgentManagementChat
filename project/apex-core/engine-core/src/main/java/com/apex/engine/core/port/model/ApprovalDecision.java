package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * The human's answer to one {@link ApprovalRequest}. A sealed outcome:
 * {@link Approve} grants the call, {@link Reject} denies it with a
 * reason, {@link Modify} asks the agent to retry with different
 * arguments. A null comment/reason is normalized to the empty string.
 * Tier 0.
 */
public sealed interface ApprovalDecision
        permits ApprovalDecision.Approve, ApprovalDecision.Reject,
        ApprovalDecision.Modify {

    /** The call is approved. */
    record Approve(String requestId, String comment) implements ApprovalDecision {
        public Approve {
            Objects.requireNonNull(requestId, "requestId");
            comment = comment == null ? "" : comment;
        }
    }

    /** The call is denied with a reason. */
    record Reject(String requestId, String reason) implements ApprovalDecision {
        public Reject {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(reason, "reason");
            reason = reason == null ? "" : reason;
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }
    }

    /**
     * The call should be retried with modified arguments. The caller
     * (Planner/Loop) feeds {@link #modifiedArguments()} back to the
     * agent as a tool result.
     */
    record Modify(String requestId, String modifiedArguments, String reason)
            implements ApprovalDecision {
        public Modify {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(modifiedArguments, "modifiedArguments");
            Objects.requireNonNull(reason, "reason");
            reason = reason == null ? "" : reason;
        }
    }

    /** True when this decision is an {@link Approve}. */
    default boolean approved() {
        return this instanceof Approve;
    }

    /** True when this decision is a {@link Reject}. */
    default boolean rejected() {
        return this instanceof Reject;
    }

    /** True when this decision is a {@link Modify}. */
    default boolean modified() {
        return this instanceof Modify;
    }
}
