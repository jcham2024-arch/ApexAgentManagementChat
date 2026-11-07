# PHASE-S2.5-STORAGE-SQLITE-PORT.md — Session Package, Part 3 of 5

**Version:** 1.0 · **Sub-phase of S2** · **Part 3 of 5:** §4.3, SqliteStoragePort.
Parts: SESSION · POM-EXCEPTION · SQLITE-PORT · METADATA-TESTS ·
CRITERIA, each `PHASE-S2.5-STORAGE-<part>.md` (`AGENT_RULES.md` §2.13).

---
### 4.3 `SqliteStoragePort.java`

```java
package com.apex.engine.store.sqlite;

import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.model.StepLogEntry;

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
 * connect. Duplicate {@code step_id} throws
 * {@link StorageException}. Do not wrap calls in a transaction — SQLite
 * serializes writes at the file level.
 *
 * <p>This adapter is for server and SBC deployments. Android uses Room
 * (S9) against the same {@link StoragePort} interface.
 */
public final class SqliteStoragePort implements StoragePort {

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS step_log (
                step_id     TEXT PRIMARY KEY,
                session_id  TEXT NOT NULL,
                step_index  INTEGER NOT NULL,
                step_type   TEXT NOT NULL,
                duration_ms INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_step_log_session
                ON step_log(session_id, step_index);
            """;

    private static final String INSERT_SQL = """
            INSERT INTO step_log
                (step_id, session_id, step_index, step_type, duration_ms)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT step_id, session_id, step_index, step_type, duration_ms
            FROM step_log
            WHERE session_id = ?
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

    private void initialize() {
        try (Connection conn = open();
             Statement st = conn.createStatement()) {
            for (String stmt : SCHEMA.split(";")) {
                if (!stmt.isBlank()) {
                    st.execute(stmt);
                }
            }
        } catch (SQLException e) {
            throw new StorageException("init", e.getMessage(), e);
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
            ps.setString(2, entry.sessionId());
            ps.setInt(3, entry.index());
            ps.setString(4, entry.stepType());
            ps.setLong(5, entry.durationMs());
            ps.executeUpdate();
        } catch (SQLException e) {
            String category = isDuplicate(e) ? "duplicate" : "append";
            throw new StorageException(category, e.getMessage(), e);
        }
    }

    @Override
    public List<StepLogEntry> stepsFor(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<StepLogEntry> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new StepLogEntry(
                            rs.getString("step_id"),
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
        // SQLite constraint violation code is 19 (SQLITE_CONSTRAINT).
        return e.getErrorCode() == 19
                || e.getMessage().toLowerCase().contains("unique");
    }
}
```
