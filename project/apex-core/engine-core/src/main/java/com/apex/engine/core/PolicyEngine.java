package com.apex.engine.core;

import com.apex.engine.core.port.RuntimePolicy;
import com.apex.engine.core.port.model.PolicyDecision;
import com.apex.engine.planner.Plan;

/**
 * The default runtime policy engine. Consulted by {@link PlanLoop} at
 * each state transition. The default implementation permits every plan;
 * stricter policies (guardrails, allow-lists, cost ceilings) are
 * implemented by composing additional {@link RuntimePolicy} instances.
 * Tier 0.
 */
public final class PolicyEngine implements RuntimePolicy {

    private PolicyEngine() {}

    /** Permits every plan (the default, fail-open only by explicit opt-in). */
    public static RuntimePolicy permissive() {
        return plan -> PolicyDecision.allow();
    }

    /** Denies every plan with the given reason (useful for maintenance windows). */
    public static RuntimePolicy denying(String reason) {
        return plan -> PolicyDecision.deny(reason);
    }

    @Override
    public PolicyDecision decide(Plan plan) {
        return PolicyDecision.allow();
    }
}
