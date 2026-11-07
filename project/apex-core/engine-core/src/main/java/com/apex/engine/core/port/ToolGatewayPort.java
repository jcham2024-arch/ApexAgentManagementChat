package com.apex.engine.core.port;

import com.apex.engine.core.port.model.AuthorizationRequest;
import com.apex.engine.core.port.model.GatewayDecision;

/**
 * The single entry point through which every tool call must pass.
 * Tier 0 port; implementations live in Tier 1 (engine-tools) and wrap a
 * {@link ToolPolicy} (default-deny) plus an {@link ApprovalPort} for
 * gated calls. The core never imports an adapter. Tier 0.
 */
public interface ToolGatewayPort {

    /**
     * Route a tool call through the gateway. The gateway consults the
     * {@link ToolPolicy}: if the call is denied it returns
     * {@link GatewayDecision.Deny}; if it requires approval it returns
     * {@link GatewayDecision.RequireApproval}; otherwise it executes the
     * call and returns {@link GatewayDecision.Approve} carrying the
     * {@link ToolResult}. Fail-closed: any exception is treated as a
     * denial.
     */
    GatewayDecision execute(AuthorizationRequest request);
}
