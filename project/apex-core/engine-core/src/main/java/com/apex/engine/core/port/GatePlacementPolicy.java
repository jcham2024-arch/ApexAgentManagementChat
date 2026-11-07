package com.apex.engine.core.port;

import com.apex.engine.core.port.model.RiskTier;
import com.apex.engine.planner.Action;
import com.apex.engine.planner.Plan;

/**
 * Decides where approval gates are placed in a plan, based on each
 * action's {@link RiskTier}. Tier 0.
 */
public interface GatePlacementPolicy {

    /**
     * Returns true if the action at {@code stepIndex} within {@code plan}
     * requires an approval gate before execution. {@code HIGH}-risk tools
     * always gate; {@code MEDIUM} gates with a timeout-to-deny; {@code LOW}
     * never gates.
     */
    boolean gateRequired(Action action, int stepIndex, Plan plan);

    /** The risk tier of an action, derived from its {@link Action#riskLevel()}. */
    default RiskTier tierOf(Action action) {
        return RiskTier.of(action.riskLevel());
    }
}
