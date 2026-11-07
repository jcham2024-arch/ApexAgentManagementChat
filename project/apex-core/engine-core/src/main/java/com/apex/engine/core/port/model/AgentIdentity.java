package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * The immutable identity of an autonomous agent principal. Distinct from
 * a human {@link com.apex.engine.core.port.model.Principal} (which lives
 * in Tier 2 auth). Tier 0: a pure value object with no I/O, threads, or
 * third-party dependencies.
 */
public record AgentIdentity(
        String agentId,
        String ownerId,
        String tenantId,
        String purpose,
        java.util.Set<String> scopes) {

    public AgentIdentity {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(scopes, "scopes");
        scopes = java.util.Set.copyOf(scopes);
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
    }
}
