package com.apex.engine.tools.impl;

import com.apex.engine.core.port.GatePlacementPolicy;
import com.apex.engine.core.port.ToolGatewayPort;
import com.apex.engine.core.port.ToolPolicy;
import com.apex.engine.core.port.model.ApprovalRequest;
import com.apex.engine.core.port.model.AuthorizationRequest;
import com.apex.engine.core.port.model.AgentIdentity;
import com.apex.engine.core.port.model.GatewayDecision;
import com.apex.engine.core.port.model.PolicyDecision;
import com.apex.engine.core.port.model.RiskTier;
import com.apex.engine.core.port.model.ToolId;
import com.apex.engine.planner.RiskLevel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KernelToolGatewayTest {

    private static final AgentIdentity IDENTITY = new AgentIdentity(
            "agent-1", "owner-1", "tenant-1", "test-agent",
            Set.of("tool:read"));

    private static final AuthorizationRequest READ_REQUEST =
            new AuthorizationRequest(IDENTITY, new ToolId("read_file"), "{}", 0);
    private static final AuthorizationRequest WRITE_REQUEST =
            new AuthorizationRequest(IDENTITY, new ToolId("write_file"), "{}", 1);
    private static final AuthorizationRequest UNKNOWN_REQUEST =
            new AuthorizationRequest(IDENTITY, new ToolId("bogus_tool"), "{}", 2);

    private ToolGatewayPort gateway(Set<String> allowed,
                                    Map<String, RiskLevel> risks) {
        ToolPolicy policy = new KernelToolGateway(allowed, risks);
        return new KernelToolGateway(allowed, risks);
    }

    @Test
    void policyDeniesUnknownTool() {
        ToolPolicy policy = new KernelToolGateway(
                Set.of("read_file"), Map.of("read_file", RiskLevel.L0));
        PolicyDecision decision = policy.authorize(UNKNOWN_REQUEST);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("unknown tool");
    }

    @Test
    void policyAllowsKnownTool() {
        ToolPolicy policy = new KernelToolGateway(
                Set.of("read_file"), Map.of("read_file", RiskLevel.L0));
        PolicyDecision decision = policy.authorize(READ_REQUEST);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void gatewayDeniesUnknownTool() {
        ToolGatewayPort gw = gateway(
                Set.of("read_file"), Map.of("read_file", RiskLevel.L0));
        GatewayDecision decision = gw.execute(UNKNOWN_REQUEST);
        assertThat(decision).isInstanceOf(GatewayDecision.Deny.class);
    }

    @Test
    void gatewayApprovesLowRiskTool() {
        ToolGatewayPort gw = gateway(
                Set.of("read_file"), Map.of("read_file", RiskLevel.L0));
        GatewayDecision decision = gw.execute(READ_REQUEST);
        assertThat(decision).isInstanceOf(GatewayDecision.Approve.class);
    }

    @Test
    void gatewayRequiresApprovalForHighRiskTool() {
        ToolGatewayPort gw = gateway(
                Set.of("run_command"), Map.of("run_command", RiskLevel.L4));
        AuthorizationRequest runCmdRequest =
                new AuthorizationRequest(IDENTITY, new ToolId("run_command"), "{}", 1);
        GatewayDecision decision = gw.execute(runCmdRequest);
        assertThat(decision).isInstanceOf(GatewayDecision.RequireApproval.class);
        GatewayDecision.RequireApproval ra =
                (GatewayDecision.RequireApproval) decision;
        assertThat(ra.request().summary()).startsWith("tool_call:");
        assertThat(ra.request().summary()).isEqualTo("tool_call:run_command");
        assertThat(ra.request().riskLevel()).isEqualTo(RiskLevel.L4);
    }

    @Test
    void riskTierRequiresApprovalOnlyForHigh() {
        assertThat(RiskTier.of(RiskLevel.L0).requiresApproval()).isFalse();
        assertThat(RiskTier.of(RiskLevel.L1).requiresApproval()).isFalse();
        assertThat(RiskTier.of(RiskLevel.L2).requiresApproval()).isFalse();
        assertThat(RiskTier.of(RiskLevel.L3).requiresApproval()).isFalse();
        assertThat(RiskTier.of(RiskLevel.L4).requiresApproval()).isTrue();
        assertThat(RiskTier.of(RiskLevel.L5).requiresApproval()).isTrue();
    }

    @Test
    void defaultRiskIsHighWhenToolNotInRegistry() {
        ToolGatewayPort gw = gateway(
                Set.of("read_file"), Map.of()); // read_file not in risk registry
        GatewayDecision decision = gw.execute(READ_REQUEST);
        assertThat(decision).isInstanceOf(GatewayDecision.RequireApproval.class);
    }

    @Test
    void gatewayReturnsIdentityInApprovalRequest() {
        ToolGatewayPort gw = gateway(
                Set.of("run_command"), Map.of("run_command", RiskLevel.L4));
        AuthorizationRequest runCmdRequest =
                new AuthorizationRequest(IDENTITY, new ToolId("run_command"), "{}", 3);
        GatewayDecision decision = gw.execute(runCmdRequest);
        assertThat(decision).isInstanceOf(GatewayDecision.RequireApproval.class);
        GatewayDecision.RequireApproval ra =
                (GatewayDecision.RequireApproval) decision;
        assertThat(ra.request().summary()).startsWith("tool_call:");
        assertThat(ra.request().summary()).isEqualTo("tool_call:run_command");
        assertThat(ra.request().riskLevel()).isEqualTo(RiskLevel.L4);
    }
}
