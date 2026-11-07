package com.apex.engine.cli;

import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;

import java.util.Objects;

/**
 * Approval port that approves every request without prompting. Used by
 * {@code --auto-approve} and by tests that need to prove the tool path
 * without interactive input. Not for production use without an
 * external policy layer.
 */
public final class AutoApprove implements ApprovalPort {

    @Override
    public ApprovalDecision request(ApprovalRequest req) {
        Objects.requireNonNull(req, "req");
        return new ApprovalDecision.Approve(req.id(), "auto-approved");
    }
}
