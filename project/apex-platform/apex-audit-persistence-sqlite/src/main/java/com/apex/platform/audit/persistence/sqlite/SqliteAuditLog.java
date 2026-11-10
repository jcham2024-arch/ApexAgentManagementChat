package com.apex.platform.audit.persistence.sqlite;

import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.model.AuditEntry;
import com.apex.platform.audit.HashChain;
import com.apex.platform.audit.PiiRedactor;

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
import java.util.UUID;

/**
 * Durable {@link AuditPort} backed by an embedded SQLite ledger.
 *
 * <p>Every append is a single transactional row insert into an append-only
 * ledger table. The HMAC-SHA256 hash chain is computed by {@link HashChain}
 * (reused from apex-audit) so hashes are byte-identical to the in-memory
 * implementation for the same inputs. PII redaction is performed by
 * {@link PiiRedactor} (also reused from apex-audit) before the entry is
 * persisted.
 *
 * <p>Chain resumption after restart is O(1): the last row's hash is read on
 * construction and cached in memory; subsequent appends commit to that hash
 * without scanning the table.
 *
 * <p>Tier 0 sees only the {@link AuditPort} interface. No SQLite, JDBC, SQL,
 * or file I/O types appear in any Tier 0 module.
 */
public final class SqliteAuditLog implements AuditPort, AutoCloseable {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS audit_log (" +
                    "sequence INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "id TEXT NOT NULL UNIQUE," +
                    "action TEXT NOT NULL," +
                    "at INTEGER NOT NULL," +
                    "previous_hash TEXT NOT NULL," +
                    "hash TEXT NOT NULL," +
                    "trace_id TEXT NOT NULL DEFAULT ''," +
                    "span_id TEXT NOT NULL DEFAULT '')";

    private static final String CREATE_NO_UPDATE_TRIGGER =
            "CREATE TRIGGER IF NOT EXISTS audit_log_no_update " +
                    "BEFORE UPDATE ON audit_log " +
                    "BEGIN SELECT RAISE(ABORT, 'audit log is append-only'); END;";

    private static final String CREATE_NO_DELETE_TRIGGER =
            "CREATE TRIGGER IF NOT EXISTS audit_log_no_delete " +
                    "BEFORE DELETE ON audit_log " +
                    "BEGIN SELECT RAISE(ABORT, 'audit log is append-only'); END;";

    private static final String INSERT_ENTRY =
            "INSERT INTO audit_log (id, action, at, previous_hash, hash, " +
                    "trace_id, span_id) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_LAST_HASH =
            "SELECT hash FROM audit_log ORDER BY sequence DESC LIMIT 1";

    private static final String SELECT_ALL =
            "SELECT id, action, at, previous_hash, hash, trace_id, span_id " +
                    "FROM audit_log ORDER BY sequence ASC";

    private final Connection connection;
    private final HashChain chain;
    private final PiiRedactor redactor;
    private String lastHash;

    /**
     * Opens (or creates) the SQLite database at {@code databasePath}, enables
     * WAL mode with full synchronous durability, and creates the append-only
     * ledger table if absent.
     *
     * @param databasePath path to the SQLite database file
     * @param hmacKey      HMAC-SHA256 key, must be at least 32 bytes
     * @throws SQLException if the database cannot be opened or initialized
     */
    public SqliteAuditLog(Path databasePath, byte[] hmacKey) throws SQLException {
        Objects.requireNonNull(databasePath, "databasePath");
        this.chain = new HashChain(hmacKey);
        this.redactor = new PiiRedactor();
        this.connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath());
        initializeSchema();
        this.lastHash = loadLastHash();
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=FULL");
            stmt.execute("PRAGMA busy_timeout=5000");
            stmt.execute(CREATE_TABLE);
            stmt.execute(CREATE_NO_UPDATE_TRIGGER);
            stmt.execute(CREATE_NO_DELETE_TRIGGER);
        }
    }

    private String loadLastHash() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_LAST_HASH)) {
            if (rs.next()) {
                return rs.getString("hash");
            }
            return "";
        }
    }

    @Override
    public synchronized AuditEntry append(String action, Instant at) {
        return append(action, at, "", "");
    }

    @Override
    public synchronized AuditEntry append(String action, Instant at,
                                          String traceId, String spanId) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(at, "at");

        String redactedAction = redactor.redact(action);
        String id = UUID.randomUUID().toString();
        long atMillis = at.toEpochMilli();

        try {
            connection.setAutoCommit(false);
            String previousHash;
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(SELECT_LAST_HASH)) {
                previousHash = rs.next() ? rs.getString("hash") : "";
            }
            String hash = chain.compute(previousHash, id, redactedAction,
                    atMillis, traceId, spanId);

            try (PreparedStatement ps = connection.prepareStatement(INSERT_ENTRY)) {
                ps.setString(1, id);
                ps.setString(2, redactedAction);
                ps.setLong(3, atMillis);
                ps.setString(4, previousHash);
                ps.setString(5, hash);
                ps.setString(6, traceId == null ? "" : traceId);
                ps.setString(7, spanId == null ? "" : spanId);
                ps.executeUpdate();
            }

            connection.commit();
            lastHash = hash;

            return new AuditEntry(id, redactedAction, at, previousHash, hash,
                    traceId, spanId);
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                // ignore; original exception is more important
            }
            throw new RuntimeException("audit append failed", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    @Override
    public synchronized List<AuditEntry> all() {
        List<AuditEntry> entries = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {
            while (rs.next()) {
                entries.add(new AuditEntry(
                        rs.getString("id"),
                        rs.getString("action"),
                        Instant.ofEpochMilli(rs.getLong("at")),
                        rs.getString("previous_hash"),
                        rs.getString("hash"),
                        rs.getString("trace_id"),
                        rs.getString("span_id")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("audit read failed", e);
        }
        return List.copyOf(entries);
    }

    @Override
    public synchronized boolean verifyChain() {
        String expectedPrevious = "";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {
            while (rs.next()) {
                String previousHash = rs.getString("previous_hash");
                if (!expectedPrevious.equals(previousHash)) {
                    return false;
                }
                String recomputed = chain.compute(previousHash,
                        rs.getString("id"), rs.getString("action"),
                        rs.getLong("at"), rs.getString("trace_id"),
                        rs.getString("span_id"));
                if (!recomputed.equals(rs.getString("hash"))) {
                    return false;
                }
                expectedPrevious = rs.getString("hash");
            }
        } catch (SQLException e) {
            throw new RuntimeException("audit verification failed", e);
        }
        return true;
    }
}
