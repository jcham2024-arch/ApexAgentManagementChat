package com.apex.engine.core.port.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A versioned workspace artifact. Tier 0.
 */
public record WorkspaceArtifact(
        String sessionId,
        String artifactId,
        String version,
        String content,
        Instant savedAt
) {
    public WorkspaceArtifact {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(savedAt, "savedAt");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (artifactId.isBlank()) {
            throw new IllegalArgumentException("artifactId must not be blank");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }
}
