package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * The outcome of a {@link RuntimePolicy} decision. {@code allowed} is
 * false only when the policy denies the plan; {@code reason} explains
 * why and is recorded in the audit chain on denial. Immutable.
 */
public record PolicyDecision(boolean allowed, String reason) {

    public PolicyDecision {
        Objects.requireNonNull(allowed, "allowed");
        reason = reason == null ? "" : reason;
    }

    /** The plan may proceed. */
    public static PolicyDecision allow() {
        return new PolicyDecision(true, null);
    }

    /** The plan is denied fail-closed with the given reason. */
    public static PolicyDecision deny(String reason) {
        Objects.requireNonNull(reason, "reason");
        return new PolicyDecision(false, reason);
    }
}
