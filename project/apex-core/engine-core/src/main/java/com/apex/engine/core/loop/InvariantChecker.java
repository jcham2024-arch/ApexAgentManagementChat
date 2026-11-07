package com.apex.engine.core.loop;

import com.apex.engine.core.port.model.InvariantSet;
import com.apex.engine.planner.Plan;

import java.util.Objects;

/**
 * Pre-execution invariant checker. Rejects any plan that violates the
 * required invariant set, fail-closed. Called by {@link PlanLoop} before
 * the first step executes so an unsafe plan never runs.
 *
 * <p>All checks are structural (plan presence, non-empty steps when
 * invariants are required, non-negative cost/depth). Enriching each
 * {@link com.apex.engine.core.port.model.Invariant} with an executable
 * predicate is a later step; this checker is the enforcement point that
 * fails closed today.
 */
public final class InvariantChecker {

    private InvariantChecker() {}

    /**
     * Checks {@code plan} against {@code required} invariants.
     *
     * @throws IllegalArgumentException if any invariant is violated
     *         (fail-closed: the plan is rejected and never executed)
     */
    public static void check(Plan plan, InvariantSet required) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(required, "required invariant set");
        if (required.isEmpty()) {
            return;
        }
        if (plan.steps().isEmpty()) {
            throw new IllegalArgumentException(
                    "plan must contain at least one step when invariants are required");
        }
        if (plan.cost() < 0) {
            throw new IllegalArgumentException("plan cost must not be negative");
        }
        if (plan.depth() < 0) {
            throw new IllegalArgumentException("plan depth must not be negative");
        }
    }
}
