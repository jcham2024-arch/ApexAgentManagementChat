package com.apex.engine.core.port;

import com.apex.engine.core.port.model.PolicyDecision;
import com.apex.engine.planner.Plan;

/**
 * Runtime policy consulted by the plan loop at each state transition.
 * A policy decides whether a plan may proceed, fail-closed: any denial
 * halts the loop deterministically and produces an audit entry. Tier 0.
 */
public interface RuntimePolicy {

    /**
     * Decides whether {@code plan} may execute.
     *
     * @return {@link PolicyDecision#allow()} to proceed, or
     *         {@link PolicyDecision#deny(String)} to halt fail-closed
     */
    PolicyDecision decide(Plan plan);
}
