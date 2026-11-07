package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * A plan node type emitted by {@link com.apex.engine.planner.AStarPlanner}
 * that represents an approval gate: execution must pause here and obtain
 * human approval (via {@link com.apex.engine.core.port.ApprovalPort})
 * before the gated tool may run. Tier 0.
 */
public record ApprovalGate(ToolId toolId, String reason) {

    public ApprovalGate {
        Objects.requireNonNull(toolId, "toolId");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
