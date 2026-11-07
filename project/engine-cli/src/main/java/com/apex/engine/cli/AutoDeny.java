package com.apex.engine.cli;

import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;

import java.util.Objects;

/**
 * Approval port that denies every request without prompting. Used by
 * tests that prove denial behaviour. May also be useful operationally
 * when an operator wants to observe the model's reaction to refusal
 * before enabling interactive approval.
 */
public final class AutoDeny implements ApprovalPort {

    @Override
    public ApprovalDecision request(ApprovalRequest req) {
        Objects.requireNonNull(req, "req");
        return new ApprovalDecision.Reject(req.id(), "auto-denied");
    }
}
