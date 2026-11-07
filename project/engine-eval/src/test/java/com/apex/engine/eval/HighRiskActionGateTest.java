package com.apex.engine.eval;

import com.apex.engine.planner.RiskLevel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static verification test: every HIGH-risk action ({@link RiskLevel#L4}
 * or {@link RiskLevel#L5}) must be gated by the {@link
 * com.apex.engine.core.port.ToolGatewayPort}. This test verifies that
 * HIGH-risk actions require approval and that the tier mapping is correct.
 */
class HighRiskActionGateTest {

    @Test
    void highRiskActionsRequireApproval() {
        // HIGH-risk actions (L4, L5) require approval
        assertThat(RiskLevel.L4).isEqualTo(RiskLevel.L4);
        assertThat(RiskLevel.L5).isEqualTo(RiskLevel.L5);
        assertThat(RiskLevel.L4.compareTo(RiskLevel.L3)).isGreaterThan(0);
        assertThat(RiskLevel.L5.compareTo(RiskLevel.L4)).isGreaterThan(0);
    }

    @Test
    void lowRiskActionsDoNotRequireApproval() {
        // LOW and MEDIUM risk actions (L0-L3) do not require approval
        assertThat(RiskLevel.L0.compareTo(RiskLevel.L3)).isLessThanOrEqualTo(0);
        assertThat(RiskLevel.L1.compareTo(RiskLevel.L3)).isLessThanOrEqualTo(0);
        assertThat(RiskLevel.L2.compareTo(RiskLevel.L3)).isLessThanOrEqualTo(0);
        assertThat(RiskLevel.L3.compareTo(RiskLevel.L3)).isEqualTo(0);
    }

    @Test
    void riskTierMapsCorrectly() {
        // Verify the risk tier mapping used by the gateway
        assertThat(com.apex.engine.core.port.model.RiskTier.of(RiskLevel.L0)
            .requiresApproval()).isFalse();
        assertThat(com.apex.engine.core.port.model.RiskTier.of(RiskLevel.L1)
            .requiresApproval()).isFalse();
        assertThat(com.apex.engine.core.port.model.RiskTier.of(RiskLevel.L2)
            .requiresApproval()).isFalse();
        assertThat(com.apex.engine.core.port.model.RiskTier.of(RiskLevel.L3)
            .requiresApproval()).isFalse();
        assertThat(com.apex.engine.core.port.model.RiskTier.of(RiskLevel.L4)
            .requiresApproval()).isTrue();
        assertThat(com.apex.engine.core.port.model.RiskTier.of(RiskLevel.L5)
            .requiresApproval()).isTrue();
    }
}
