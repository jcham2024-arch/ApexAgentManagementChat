package com.apex.engine.store.sqlite;

import com.apex.engine.core.port.WorkspacePort;
import com.apex.engine.core.port.model.WorkspaceArtifact;
import com.apex.engine.store.StorageException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite-backed {@link WorkspacePort}. Stores versioned artifacts keyed by
 * {@code (sessionId, artifactId, version)}. Tier 1.
 */
public final class SqliteWorkspaceStore implements WorkspacePort {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS workspace_artifacts (
                session_id    TEXT NOT NULL,
                artifact_id   TEXT NOT NULL,
                version       TEXT NOT NULL,
                content       TEXT NOT NULL,
                saved_at      INTEGER NOT NULL,
                PRIMARY KEY (session_id, artifact_id, version)
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO workspace_artifacts (session_id, artifact_id, version,
                                             content, saved_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT session_id, artifact_id, version, content, saved_at
            FROM workspace_artifacts
            WHERE session_id = ? AND artifact_id = ? AND version = ?
            """;

    private static final String SELECT_LATEST_SQL = """
            SELECT session_id, artifact_id, version, content, saved_at
            FROM workspace_artifacts
            WHERE session_id = ? AND artifact_id = ?
            ORDER BY saved_at DESC
            LIMIT 1
            """;

    private static final String LIST_ARTIFACTS_SQL = """
            SELECT DISTINCT artifact_id
            FROM workspace_artifacts
            WHERE session_id = ?
            ORDER BY artifact_id
            """;

    private static final String DELETE_SQL = """
            DELETE FROM workspace_artifacts
            WHERE session_id = ? AND artifact_id = ? AND version = ?
            """;

    private final java.nio.file.Path dbPath;

    public SqliteWorkspaceStore(java.nio.file.Path dbPath) {
        this.dbPath = java.util.Objects.requireNonNull(dbPath, "dbPath");
        initSchema();
    }

    private Connection open() throws SQLException {
        return java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private void initSchema() {
        try (Connection conn = open();
             Statement st = conn.createStatement()) {
            st.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new StorageException("init", "failed to initialize schema", e);
        }
    }

    @Override
    public WorkspaceArtifact writeArtifact(String sessionId, String artifactId,
                                           String content) {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        java.util.Objects.requireNonNull(artifactId, "artifactId");
        java.util.Objects.requireNonNull(content, "content");
        String version = java.util.UUID.randomUUID().toString();
        Instant now = Instant.now();
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, sessionId);
            ps.setString(2, artifactId);
            ps.setString(3, version);
            ps.setString(4, content);
            ps.setLong(5, now.toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("store",
                    "failed to write artifact " + artifactId, e);
        }
        return new WorkspaceArtifact(sessionId, artifactId, version, content, now);
    }

    @Override
    public WorkspaceArtifact readArtifact(String sessionId, String artifactId,
                                          String version) {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        java.util.Objects.requireNonNull(artifactId, "artifactId");
        java.util.Objects.requireNonNull(version, "version");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(
                     "latest".equals(version) ? SELECT_LATEST_SQL : SELECT_SQL)) {
            ps.setString(1, sessionId);
            ps.setString(2, artifactId);
            if (!"latest".equals(version)) {
                ps.setString(3, version);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new StorageException("query",
                            "artifact not found: " + artifactId + "@" + version);
                }
                return new WorkspaceArtifact(
                        rs.getString("session_id"),
                        rs.getString("artifact_id"),
                        rs.getString("version"),
                        rs.getString("content"),
                        Instant.ofEpochMilli(rs.getLong("saved_at"))
                );
            }
        } catch (SQLException e) {
            throw new StorageException("query",
                    "failed to read artifact " + artifactId, e);
        }
    }

    @Override
    public java.util.List<String> listArtifacts(String sessionId) {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(LIST_ARTIFACTS_SQL)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> artifacts = new ArrayList<>();
                while (rs.next()) {
                    artifacts.add(rs.getString("artifact_id"));
                }
                return artifacts;
            }
        } catch (SQLException e) {
            throw new StorageException("query",
                    "failed to list artifacts for session " + sessionId, e);
        }
    }

    @Override
    public void deleteArtifact(String sessionId, String artifactId,
                               String version) {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        java.util.Objects.requireNonNull(artifactId, "artifactId");
        java.util.Objects.requireNonNull(version, "version");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, sessionId);
            ps.setString(2, artifactId);
            ps.setString(3, version);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("delete",
                    "failed to delete artifact " + artifactId, e);
        }
    }
}
