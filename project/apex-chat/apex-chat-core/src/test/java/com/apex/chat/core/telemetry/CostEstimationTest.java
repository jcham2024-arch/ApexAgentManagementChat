package com.apex.chat.core.telemetry;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostEstimationTest {

    @Test
    void createsValidCostEstimation() {
        CostEstimation cost = new CostEstimation(
                new BigDecimal("0.001500"),
                new BigDecimal("0.006000"),
                new BigDecimal("0.003000"),
                "USD", "tier-1");

        assertThat(cost.inputCost()).isEqualByComparingTo("0.001500");
        assertThat(cost.outputCost()).isEqualByComparingTo("0.006000");
        assertThat(cost.reasoningCost()).isEqualByComparingTo("0.003000");
        assertThat(cost.currency()).isEqualTo("USD");
        assertThat(cost.pricingTier()).isEqualTo("tier-1");
        assertThat(cost.total()).isEqualByComparingTo("0.010500");
    }

    @Test
    void totalSumsAllComponents() {
        CostEstimation cost = new CostEstimation(
                new BigDecimal("1.00"),
                new BigDecimal("2.00"),
                new BigDecimal("3.00"),
                "USD", "tier-1");

        assertThat(cost.total()).isEqualByComparingTo("6.00");
    }

    @Test
    void zeroCostsAreValid() {
        CostEstimation cost = new CostEstimation(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", "free");

        assertThat(cost.total()).isEqualByComparingTo("0.000000");
    }

    @Test
    void rejectsNegativeInputCost() {
        assertThatThrownBy(() -> new CostEstimation(
                new BigDecimal("-1.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                "USD", "tier-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeOutputCost() {
        assertThatThrownBy(() -> new CostEstimation(
                new BigDecimal("0.00"),
                new BigDecimal("-1.00"),
                new BigDecimal("0.00"),
                "USD", "tier-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> new CostEstimation(
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                null, "tier-1"))
                .isInstanceOf(NullPointerException.class);
    }
}
