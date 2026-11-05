package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * The human's answer to one {@link ApprovalRequest}. A null comment is
 * normalized to the empty string.
 */
public record ApprovalDecision(String requestId, boolean approved,
                               String comment) {
    public ApprovalDecision {
        Objects.requireNonNull(requestId, "requestId");
        comment = comment == null ? "" : comment;
    }
}
