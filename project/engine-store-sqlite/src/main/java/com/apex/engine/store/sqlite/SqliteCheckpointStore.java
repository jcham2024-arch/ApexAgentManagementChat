package com.apex.engine.store.sqlite;

import com.apex.engine.core.port.CheckpointPort;
import com.apex.engine.core.port.model.CheckpointRecord;
import com.apex.engine.store.StorageException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite-backed {@link CheckpointPort}. Stores one checkpoint per session
 * (sessionId is the primary key). Saving replaces any existing checkpoint
 * for the same session. Tier 1.
 */
public final class SqliteCheckpointStore implements CheckpointPort {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS checkpoints (
                session_id     TEXT PRIMARY KEY,
                step_index     INTEGER NOT NULL,
                state_snapshot TEXT NOT NULL,
                saved_at       INTEGER NOT NULL
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO checkpoints (session_id, step_index, state_snapshot,
                                     saved_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(session_id) DO UPDATE SET
                step_index     = excluded.step_index,
                state_snapshot = excluded.state_snapshot,
                saved_at       = excluded.saved_at
            """;

    private static final String SELECT_SQL = """
            SELECT session_id, step_index, state_snapshot, saved_at
            FROM checkpoints
            WHERE session_id = ?
            """;

    private static final String DELETE_SQL = """
            DELETE FROM checkpoints WHERE session_id = ?
            """;

    private final java.nio.file.Path dbPath;

    public SqliteCheckpointStore(java.nio.file.Path dbPath) {
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
    public void saveCheckpoint(String sessionId, int stepIndex,
                               String stateSnapshot) {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        java.util.Objects.requireNonNull(stateSnapshot, "stateSnapshot");
        if (stepIndex < 0) {
            throw new IllegalArgumentException("stepIndex must not be negative");
        }
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, sessionId);
            ps.setInt(2, stepIndex);
            ps.setString(3, stateSnapshot);
            ps.setLong(4, Instant.now().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("store",
                    "failed to save checkpoint for session " + sessionId, e);
        }
    }

    @Override
    public java.util.Optional<CheckpointRecord> restoreCheckpoint(
            String sessionId) {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return java.util.Optional.empty();
                }
                return java.util.Optional.of(new CheckpointRecord(
                        rs.getString("session_id"),
                        rs.getInt("step_index"),
                        rs.getString("state_snapshot"),
                        Instant.ofEpochMilli(rs.getLong("saved_at"))
                ));
            }
        } catch (SQLException e) {
            throw new StorageException("query",
                    "failed to restore checkpoint for session " + sessionId, e);
        }
    }

    @Override
    public void deleteCheckpoint(String sessionId) {
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("delete",
                    "failed to delete checkpoint for session " + sessionId, e);
        }
    }
}
