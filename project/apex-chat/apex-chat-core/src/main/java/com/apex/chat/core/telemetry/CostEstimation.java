package com.apex.chat.core.telemetry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Cost estimate for a single LLM generation. All monetary values use
 * {@link BigDecimal} (never {@code double}) to avoid rounding errors.
 * Values are pinned to {@link RoundingMode#HALF_UP} at 6 decimal places.
 */
public record CostEstimation(
    BigDecimal inputCost,
    BigDecimal outputCost,
    BigDecimal reasoningCost,
    String currency,
    String pricingTier
) {
    public CostEstimation {
        Objects.requireNonNull(inputCost, "inputCost");
        Objects.requireNonNull(outputCost, "outputCost");
        Objects.requireNonNull(reasoningCost, "reasoningCost");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(pricingTier, "pricingTier");
        if (inputCost.signum() < 0) throw new IllegalArgumentException("inputCost must be >= 0");
        if (outputCost.signum() < 0) throw new IllegalArgumentException("outputCost must be >= 0");
        if (reasoningCost.signum() < 0) throw new IllegalArgumentException("reasoningCost must be >= 0");
        if (currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
        if (pricingTier.isBlank()) throw new IllegalArgumentException("pricingTier must not be blank");
    }

    /**
     * Total estimated cost: input + output + reasoning.
     * Result is rounded to 6 decimal places using HALF_UP.
     */
    public BigDecimal total() {
        return inputCost
            .add(outputCost)
            .add(reasoningCost)
            .setScale(6, RoundingMode.HALF_UP);
    }
}
