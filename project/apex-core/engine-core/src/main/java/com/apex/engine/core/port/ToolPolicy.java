package com.apex.engine.core.port;

import com.apex.engine.core.port.model.AuthorizationRequest;
import com.apex.engine.core.port.model.PolicyDecision;

/**
 * Decides whether a tool call may proceed. Default-deny: a policy
 * returns {@link PolicyDecision#allow()} only for calls it explicitly
 * permits; anything not explicitly allowed is denied. Tier 0.
 */
public interface ToolPolicy {

    /**
     * Authorize a tool call before it is executed.
     *
     * @return {@link PolicyDecision#allow()} to proceed, or
     *         {@link PolicyDecision#deny(String)} to deny fail-closed
     */
    PolicyDecision authorize(AuthorizationRequest request);
}
