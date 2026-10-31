package com.apex.engine.core.port;

import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;

/**
 * Synchronous human-in-the-loop approval. Tier 1 impl: engine-cli
 * (S2.6, terminal prompt). Tier 2 impl: adapter-spring-ai (S3, SSE).
 * Event-driven approval lands in S3 as an additive interface.
 */
public interface ApprovalPort {
    ApprovalDecision request(ApprovalRequest request);
}
