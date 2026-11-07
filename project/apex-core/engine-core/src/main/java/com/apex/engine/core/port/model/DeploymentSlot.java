package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * Deployment slot for canary rollouts. Staging is the pre-production
 * environment; production is the live environment. Tier 0.
 */
public enum DeploymentSlot {
    STAGING,
    PRODUCTION;

    /** True if this slot is a production slot. */
    public boolean isProduction() {
        return this == PRODUCTION;
    }
}
