package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * Immutable, versioned description of an agent deployment. Pinned into a
 * plan at session start so two sessions with the same manifest are
 * reproducible; a manifest change is a new versioned artifact and the
 * prior version remains resolvable. Tier 0.
 */
public record AgentVersionManifest(
        String id,
        String modelConfigRef,
        String systemPromptHash,
        String toolSetHash,
        String evalBaselineRef,
        String priorManifestHash,
        long sequence) {

    public AgentVersionManifest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(modelConfigRef, "modelConfigRef");
        Objects.requireNonNull(systemPromptHash, "systemPromptHash");
        Objects.requireNonNull(toolSetHash, "toolSetHash");
        Objects.requireNonNull(evalBaselineRef, "evalBaselineRef");
        Objects.requireNonNull(priorManifestHash, "priorManifestHash");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (modelConfigRef.isBlank()) {
            throw new IllegalArgumentException("modelConfigRef must not be blank");
        }
        if (systemPromptHash.isBlank()) {
            throw new IllegalArgumentException("systemPromptHash must not be blank");
        }
        if (toolSetHash.isBlank()) {
            throw new IllegalArgumentException("toolSetHash must not be blank");
        }
        if (evalBaselineRef.isBlank()) {
            throw new IllegalArgumentException("evalBaselineRef must not be blank");
        }
        if (priorManifestHash.isBlank()) {
            throw new IllegalArgumentException("priorManifestHash must not be blank");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
    }

    /** The first manifest in a chain: no prior version. */
    public static AgentVersionManifest genesis(String id,
                                               String modelConfigRef,
                                               String systemPromptHash,
                                               String toolSetHash,
                                               String evalBaselineRef) {
        return new AgentVersionManifest(id, modelConfigRef, systemPromptHash,
                toolSetHash, evalBaselineRef, "", 0);
    }
}
