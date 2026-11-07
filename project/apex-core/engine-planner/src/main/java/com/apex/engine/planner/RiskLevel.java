package com.apex.engine.planner;

/**
 * Risk tier of an action. L0 is read-only; L5 is privilege escalation.
 * The planner weights higher-risk actions with a higher cost so a
 * lower-risk path is preferred when total base cost is equal.
 */
public enum RiskLevel {
    L0, L1, L2, L3, L4, L5;

    /** Risk penalty added to the base cost during planning. */
    public double penalty() {
        return switch (this) {
            case L0 -> 0.0;
            case L1 -> 0.1;
            case L2 -> 0.3;
            case L3 -> 1.0;
            case L4 -> 3.0;
            case L5 -> 10.0;
        };
    }

    /** True if this tier requires human approval before execution. */
    public boolean requiresApproval() {
        return this == L3 || this == L4 || this == L5;
    }
}
