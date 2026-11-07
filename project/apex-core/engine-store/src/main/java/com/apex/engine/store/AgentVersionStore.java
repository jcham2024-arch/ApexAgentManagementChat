package com.apex.engine.store;

import com.apex.engine.core.port.model.AgentVersionManifest;

/**
 * Persistence for versioned agent manifests. Every manifest is
 * versioned and append-only: storing a manifest never overwrites an
 * existing version, and rollback returns a prior manifest by sequence.
 * Tier 0 port; implementations live in adapters (e.g. engine-store-sqlite).
 */
public interface AgentVersionStore {

    /**
     * Resolves the manifest for {@code id}. Returns {@link
     * java.util.Optional#empty()} when no manifest is stored.
     */
    java.util.Optional<AgentVersionManifest> resolve(String id);

    /** Stores {@code manifest}. Fail-closed: throws if the manifest is
     *  already stored for {@code manifest.id()} with a different hash. */
    void store(AgentVersionManifest manifest);

    /**
     * Rolls back to the manifest at {@code sequence}. Returns
     * {@link java.util.Optional#empty()} when no such version exists.
     */
    java.util.Optional<AgentVersionManifest> rollback(String id, long sequence);
}
