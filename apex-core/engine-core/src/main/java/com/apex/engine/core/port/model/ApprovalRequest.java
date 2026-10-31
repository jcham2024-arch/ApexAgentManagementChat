package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * A human approval request. {@code riskLevel} is one of {@code L0} to
 * {@code L5}; S3 defines the ladder fully, so it is a plain string
 * here.
 */
public record ApprovalRequest(String id, String summary, String riskLevel) {
    public ApprovalRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(riskLevel, "riskLevel");
    }
}
