package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * Traffic policy for deterministic canary routing. Routes sessions to
 * staging or production based on a hash of the session ID (no RNG).
 * Tier 0.
 */
public record TrafficPolicy(
        double stagingPercentage,
        String hashAlgorithm
) {
    public TrafficPolicy {
        Objects.requireNonNull(hashAlgorithm, "hashAlgorithm");
        if (stagingPercentage < 0.0 || stagingPercentage > 100.0) {
            throw new IllegalArgumentException(
                "stagingPercentage must be between 0 and 100");
        }
    }

    /**
     * Route a session to a deployment slot based on a hash of the session
     * ID. Same session always routes to the same slot.
     */
    public DeploymentSlot route(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        int hash = sessionId.hashCode();
        double normalized = Math.abs(hash) % 100;
        return normalized < stagingPercentage
            ? DeploymentSlot.STAGING
            : DeploymentSlot.PRODUCTION;
    }
}
