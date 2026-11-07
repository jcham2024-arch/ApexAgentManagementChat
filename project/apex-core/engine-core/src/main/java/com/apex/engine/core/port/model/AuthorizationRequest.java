package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * A request to execute one tool through the {@link ToolGatewayPort}.
 * Carries the calling agent identity, the target tool, the JSON
 * arguments, and the plan step index so every invocation is auditable
 * and attributable. Tier 0.
 */
public record AuthorizationRequest(
        AgentIdentity identity,
        ToolId toolId,
        String argumentsJson,
        int stepIndex) {

    public AuthorizationRequest {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(toolId, "toolId");
        Objects.requireNonNull(argumentsJson, "argumentsJson");
        if (stepIndex < 0) {
            throw new IllegalArgumentException("stepIndex must not be negative");
        }
    }
}
