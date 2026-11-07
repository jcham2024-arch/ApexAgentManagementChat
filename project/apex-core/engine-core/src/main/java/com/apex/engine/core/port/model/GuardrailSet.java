package com.apex.engine.core.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Guardrails carried by a plan and enforced by {@link PlanLoop} at each
 * step boundary. A guardrail breach is a named, non-exceptional
 * outcome (a distinct {@link com.apex.engine.core.loop.PlanStatus}),
 * not an exception. Immutable.
 */
public record GuardrailSet(
        Integer maxSteps,
        Long tokenBudgetCents,
        Long costCeilingCents,
        List<String> allowedTools) {

    public GuardrailSet {
        maxSteps = maxSteps == null ? Integer.MAX_VALUE : maxSteps;
        if (maxSteps < 0) {
            throw new IllegalArgumentException("maxSteps must not be negative");
        }
        tokenBudgetCents = tokenBudgetCents == null ? Long.MAX_VALUE : tokenBudgetCents;
        if (tokenBudgetCents < 0) {
            throw new IllegalArgumentException("tokenBudgetCents must not be negative");
        }
        costCeilingCents = costCeilingCents == null ? Long.MAX_VALUE : costCeilingCents;
        if (costCeilingCents < 0) {
            throw new IllegalArgumentException("costCeilingCents must not be negative");
        }
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    /** Default guardrails: no limits, no tool restrictions. */
    public static GuardrailSet none() {
        return new GuardrailSet(null, null, null, null);
    }

    /** Returns true when every guardrail field is at its default (unlimited). */
    public boolean isUnlimited() {
        return maxSteps == Integer.MAX_VALUE
                && tokenBudgetCents == Long.MAX_VALUE
                && costCeilingCents == Long.MAX_VALUE
                && allowedTools.isEmpty();
    }
}
