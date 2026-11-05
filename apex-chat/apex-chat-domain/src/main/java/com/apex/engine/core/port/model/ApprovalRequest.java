package com.apex.engine.core.port.model;

import com.apex.engine.planner.RiskLevel;

import java.util.Objects;

/**
 * A request for human approval of a tool call. {@link #riskLevel}
 * determines whether approval is required: L3–L5 require it, L0–L2 do
 * not (enforced by {@link RiskLevel#requiresApproval()}). The caller
 * (S3.4 HITL gate) is responsible for skipping requests on L0–L2.
 */
public record ApprovalRequest(String id, String summary, RiskLevel riskLevel) {
    public ApprovalRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(riskLevel, "riskLevel");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
