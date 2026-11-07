package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * The risk tier of a tool. A simplified, human-readable classification
 * layered on top of {@link com.apex.engine.planner.RiskLevel}: {@code LOW}
 * maps to L0\u2013L1, {@code MEDIUM} to L2\u2013L3, {@code HIGH} to L4\u2013L5.
 * {@link #requiresApproval()} is true only for {@code HIGH}. Tier 0.
 */
public enum RiskTier {
    LOW,
    MEDIUM,
    HIGH;

    /** True if this tier requires human approval before execution. */
    public boolean requiresApproval() {
        return this == HIGH;
    }

    /** Map an engine {@link RiskLevel} to a {@link RiskTier}. */
    public static RiskTier of(com.apex.engine.planner.RiskLevel level) {
        return switch (level) {
            case L0, L1 -> LOW;
            case L2, L3 -> MEDIUM;
            case L4, L5 -> HIGH;
        };
    }
}
