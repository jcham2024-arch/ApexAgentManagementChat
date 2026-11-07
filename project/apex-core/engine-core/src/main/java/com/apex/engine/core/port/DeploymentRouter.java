package com.apex.engine.core.port;

import com.apex.engine.core.port.model.DeploymentSlot;
import com.apex.engine.core.port.model.TrafficPolicy;

/**
 * Deployment router: deterministic canary routing based on traffic policy.
 * Routes sessions to staging or production slots. Tier 0.
 */
public interface DeploymentRouter {

    /**
     * Route a session to a deployment slot based on the traffic policy.
     */
    DeploymentSlot route(String sessionId, TrafficPolicy policy);

    /** The current deployment slot for rollout decisions. */
    DeploymentSlot currentSlot();
}
