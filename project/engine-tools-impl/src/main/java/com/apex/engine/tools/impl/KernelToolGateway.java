package com.apex.engine.tools.impl;

import com.apex.engine.core.port.GatePlacementPolicy;
import com.apex.engine.core.port.ToolGatewayPort;
import com.apex.engine.core.port.ToolPolicy;
import com.apex.engine.core.port.model.ApprovalRequest;
import com.apex.engine.core.port.model.AuthorizationRequest;
import com.apex.engine.core.port.model.GatewayDecision;
import com.apex.engine.core.port.model.PolicyDecision;
import com.apex.engine.core.port.model.RiskTier;
import com.apex.engine.core.port.model.ToolId;
import com.apex.engine.planner.RiskLevel;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tier 1 adapter implementing {@link ToolGatewayPort} and {@link ToolPolicy}.
 * Default-deny: only tools in the allow list may proceed. Tools with
 * {@link RiskTier#HIGH} require human approval before execution; {@link
 * RiskTier#LOW} and {@link RiskTier#MEDIUM} proceed without approval.
 * Tier 1: depends on engine-core ports and engine-planner types only.
 */
public final class KernelToolGateway implements ToolGatewayPort, ToolPolicy {

    private final Set<String> allowedTools;
    private final Map<String, RiskLevel> riskRegistry;

    public KernelToolGateway(Set<String> allowedTools,
                             Map<String, RiskLevel> riskRegistry) {
        this.allowedTools = Set.copyOf(
                Objects.requireNonNull(allowedTools, "allowedTools"));
        this.riskRegistry = Map.copyOf(
                Objects.requireNonNull(riskRegistry, "riskRegistry"));
    }

    // ---- ToolPolicy: default-deny authorization ----

    @Override
    public PolicyDecision authorize(AuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!allowedTools.contains(request.toolId().value())) {
            return PolicyDecision.deny("unknown tool: " + request.toolId());
        }
        return PolicyDecision.allow();
    }

    // ---- ToolGatewayPort: route through policy, then risk-based gating ----

    @Override
    public GatewayDecision execute(AuthorizationRequest request) {
        Objects.requireNonNull(request, "request");

        // Step 1: policy check - default deny.
        PolicyDecision policyDecision = authorize(request);
        if (!policyDecision.allowed()) {
            return new GatewayDecision.Deny(policyDecision.reason());
        }

        // Step 2: risk-based gating. HIGH risk requires human approval;
        // LOW and MEDIUM proceed without approval.
        RiskLevel level = riskRegistry.getOrDefault(
                request.toolId().value(), RiskLevel.L4);
        if (RiskTier.of(level).requiresApproval()) {
            return new GatewayDecision.RequireApproval(
                    new ApprovalRequest(
                            UUID.randomUUID().toString(),
                            "tool_call:" + request.toolId().value(),
                            level));
        }

        return new GatewayDecision.Approve();
    }
}
