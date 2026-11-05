package com.apex.engine.store.sqlite;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.model.StepLogEntry;
import com.apex.engine.store.StorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SQLite implementation of {@link StoragePort}. Each operation opens
 * its own JDBC connection; WAL mode and a busy timeout are applied on
 * connect. Every query filters by {@code tenant_id} — a query for
 * tenant A never returns rows written by tenant B, regardless of the
 * session id passed in.
 */
public final class SqliteStoragePort implements StoragePort {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS step_log (
                step_id     TEXT PRIMARY KEY,
                tenant_id   TEXT NOT NULL,
                session_id  TEXT NOT NULL,
                step_index  INTEGER NOT NULL,
                step_type   TEXT NOT NULL,
                duration_ms INTEGER NOT NULL
            )
            """;

    private static final String CREATE_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_step_log_tenant_session
                ON step_log(tenant_id, session_id, step_index)
            """;

    private static final String MIGRATE_ADD_TENANT = """
            ALTER TABLE step_log
            ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'legacy-migration'
            """;

    private static final String INSERT_SQL = """
            INSERT INTO step_log
                (step_id, tenant_id, session_id, step_index, step_type,
                 duration_ms)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT step_id, tenant_id, session_id, step_index, step_type,
                   duration_ms
            FROM step_log
            WHERE tenant_id = ? AND session_id = ?
            ORDER BY step_index ASC
            """;

    private final Path dbPath;

    public SqliteStoragePort(Path dbPath) {
        Objects.requireNonNull(dbPath, "dbPath");
        this.dbPath = dbPath.toAbsolutePath();
        Path parent = this.dbPath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new StorageException("init",
                        "cannot create database directory: " + parent, e);
            }
        }
        initialize();
    }

    /**
     * Creates the table, migrates a pre-S3.2 database, then creates the
     * index. The order matters: the index names {@code tenant_id}, so it
     * cannot be created before the migration has added that column. The
     * phase file's single schema string created both together and failed
     * on legacy databases with "no such column: tenant_id".
     */
    private void initialize() {
        try (Connection conn = open();
             Statement st = conn.createStatement()) {
            st.execute(CREATE_TABLE);
            if (!hasTenantColumn(conn)) {
                st.execute(MIGRATE_ADD_TENANT);
            }
            st.execute(CREATE_INDEX);
        } catch (SQLException e) {
            throw new StorageException("init", e.getMessage(), e);
        }
    }

    private static boolean hasTenantColumn(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(step_log)")) {
            while (rs.next()) {
                if ("tenant_id".equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private Connection open() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=5000");
        }
        return conn;
    }

    @Override
    public void appendStep(StepLogEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, entry.stepId());
            ps.setString(2, entry.tenantId().value());
            ps.setString(3, entry.sessionId());
            ps.setInt(4, entry.index());
            ps.setString(5, entry.stepType());
            ps.setLong(6, entry.durationMs());
            ps.executeUpdate();
        } catch (SQLException e) {
            String category = isDuplicate(e) ? "duplicate" : "append";
            throw new StorageException(category, e.getMessage(), e);
        }
    }

    @Override
    public List<StepLogEntry> stepsFor(TenantId tenantId, String sessionId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, tenantId.value());
            ps.setString(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<StepLogEntry> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new StepLogEntry(
                            rs.getString("step_id"),
                            new TenantId(rs.getString("tenant_id")),
                            rs.getString("session_id"),
                            rs.getInt("step_index"),
                            rs.getString("step_type"),
                            rs.getLong("duration_ms")));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new StorageException("query", e.getMessage(), e);
        }
    }

    private static boolean isDuplicate(SQLException e) {
        return e.getErrorCode() == 19
                || e.getMessage().toLowerCase().contains("unique");
    }
}
