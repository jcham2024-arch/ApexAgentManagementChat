package com.apex.engine.store.sqlite;

import com.apex.engine.core.port.model.AgentVersionManifest;
import com.apex.engine.store.AgentVersionStore;
import com.apex.engine.store.StorageException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite-backed {@link AgentVersionStore}. Manifests are append-only and
 * versioned by {@code sequence}: storing never overwrites an existing
 * version, and {@link #rollback} returns a prior manifest by sequence.
 * Tier 1.
 */
public final class SqliteAgentVersionStore implements AgentVersionStore {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS agent_version_manifest (
                id                TEXT NOT NULL,
                sequence          INTEGER NOT NULL,
                model_config_ref  TEXT NOT NULL,
                system_prompt_hash TEXT NOT NULL,
                tool_set_hash     TEXT NOT NULL,
                eval_baseline_ref TEXT NOT NULL,
                prior_hash        TEXT NOT NULL,
                PRIMARY KEY (id, sequence)
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO agent_version_manifest
                (id, sequence, model_config_ref, system_prompt_hash,
                 tool_set_hash, eval_baseline_ref, prior_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_LATEST = """
            SELECT id, model_config_ref, system_prompt_hash, tool_set_hash,
                   eval_baseline_ref, prior_hash, sequence
            FROM agent_version_manifest
            WHERE id = ?
            ORDER BY sequence DESC
            LIMIT 1
            """;

    private static final String SELECT_BY_SEQUENCE = """
            SELECT id, model_config_ref, system_prompt_hash, tool_set_hash,
                   eval_baseline_ref, prior_hash, sequence
            FROM agent_version_manifest
            WHERE id = ? AND sequence = ?
            """;

    private static final String EXISTS = """
            SELECT 1 FROM agent_version_manifest
            WHERE id = ? AND sequence = ?
            LIMIT 1
            """;

    private final Path dbPath;

    public SqliteAgentVersionStore(Path dbPath) {
        this.dbPath = Objects.requireNonNull(dbPath, "dbPath");
        initSchema();
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
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
    public Optional<AgentVersionManifest> resolve(String id) {
        Objects.requireNonNull(id, "id");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(SELECT_LATEST)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(toManifest(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("query", "failed to resolve manifest " + id, e);
        }
    }

    @Override
    public void store(AgentVersionManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try {
                if (manifestExists(conn, manifest.id(), manifest.sequence())) {
                    throw new StorageException("store",
                            "manifest already stored: " + manifest.id()
                            + "@" + manifest.sequence());
                }
                try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                    ps.setString(1, manifest.id());
                    ps.setLong(2, manifest.sequence());
                    ps.setString(3, manifest.modelConfigRef());
                    ps.setString(4, manifest.systemPromptHash());
                    ps.setString(5, manifest.toolSetHash());
                    ps.setString(6, manifest.evalBaselineRef());
                    ps.setString(7, manifest.priorManifestHash());
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    // ignore; original exception is more important
                }
                throw new StorageException("store", "failed to store manifest "
                        + manifest.id(), e);
            }
        } catch (SQLException e) {
            throw new StorageException("init", "failed to open connection", e);
        }
    }

    @Override
    public Optional<AgentVersionManifest> rollback(String id, long sequence) {
        Objects.requireNonNull(id, "id");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_SEQUENCE)) {
            ps.setString(1, id);
            ps.setLong(2, sequence);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(toManifest(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("query", "failed to rollback manifest "
                    + id + "@" + sequence, e);
        }
    }

    private boolean manifestExists(Connection conn, String id, long sequence)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(EXISTS)) {
            ps.setString(1, id);
            ps.setLong(2, sequence);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private AgentVersionManifest toManifest(ResultSet rs) throws SQLException {
        return new AgentVersionManifest(
                rs.getString("id"),
                rs.getString("model_config_ref"),
                rs.getString("system_prompt_hash"),
                rs.getString("tool_set_hash"),
                rs.getString("eval_baseline_ref"),
                rs.getString("prior_hash"),
                rs.getLong("sequence"));
    }
}
