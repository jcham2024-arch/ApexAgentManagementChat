package com.apex.engine.core.port;

import com.apex.engine.core.port.model.WorkspaceArtifact;

import java.util.List;

/**
 * Workspace port: versioned, addressable artifacts keyed by
 * {@code (sessionId, artifactId, version)}. Tier 0.
 */
public interface WorkspacePort {

    /**
     * Write an artifact for the given session. Returns the artifact record
     * with its assigned version. Fail-closed: throws if the artifact
     * cannot be persisted.
     */
    WorkspaceArtifact writeArtifact(String sessionId, String artifactId,
                                    String content);

    /**
     * Read an artifact at the given version. {@code version} may be
     * "latest" to get the most recent version.
     */
    WorkspaceArtifact readArtifact(String sessionId, String artifactId,
                                   String version);

    /** List all artifact IDs for the given session. */
    List<String> listArtifacts(String sessionId);

    /** Delete an artifact version. */
    void deleteArtifact(String sessionId, String artifactId, String version);
}
