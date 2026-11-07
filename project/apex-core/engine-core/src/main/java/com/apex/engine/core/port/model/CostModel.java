package com.apex.engine.core.port.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Monetary cost model for a single LLM generation or tool step. All
 * monetary values use {@link BigDecimal} (never {@code double}) to avoid
 * rounding errors, pinned to {@link RoundingMode#HALF_UP} at 6 decimal
 * places. Immutable.
 */
public record CostModel(
        BigDecimal promptTokens,
        BigDecimal completionTokens,
        BigDecimal promptCostCents,
        BigDecimal completionCostCents) {

    public CostModel {
        Objects.requireNonNull(promptTokens, "promptTokens");
        Objects.requireNonNull(completionTokens, "completionTokens");
        Objects.requireNonNull(promptCostCents, "promptCostCents");
        Objects.requireNonNull(completionCostCents, "completionCostCents");
        if (promptTokens.signum() < 0) {
            throw new IllegalArgumentException("promptTokens must be >= 0");
        }
        if (completionTokens.signum() < 0) {
            throw new IllegalArgumentException("completionTokens must be >= 0");
        }
        if (promptCostCents.signum() < 0) {
            throw new IllegalArgumentException("promptCostCents must be >= 0");
        }
        if (completionCostCents.signum() < 0) {
            throw new IllegalArgumentException("completionCostCents must be >= 0");
        }
    }

    /** Total token count across prompt and completion. */
    public BigDecimal totalTokens() {
        return promptTokens.add(completionTokens);
    }

    /** Total estimated cost in cents, rounded HALF_UP to 6 dp. */
    public BigDecimal totalCostCents() {
        return promptCostCents
                .add(completionCostCents)
                .setScale(6, RoundingMode.HALF_UP);
    }

    /** Empty cost (no tokens, no cost). */
    public static CostModel none() {
        return new CostModel(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
