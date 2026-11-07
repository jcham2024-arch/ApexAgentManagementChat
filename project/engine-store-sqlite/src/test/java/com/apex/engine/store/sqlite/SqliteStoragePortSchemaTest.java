package com.apex.engine.store.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Raw-JDBC inspection of the schema and the connection pragmas. Kept
 * apart from SqliteStoragePortTest so both files stay well under the
 * 200-line limit.
 */
class SqliteStoragePortSchemaTest {

    @TempDir
    Path tempDir;

    Path dbPath;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test.db");
        new SqliteStoragePort(dbPath);
    }

    @Test
    void schemaHasExactlyTheFiveStepLogColumns() throws Exception {
        Set<String> columns = new TreeSet<>();
        try (Connection conn = connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(step_log)")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }

        assertThat(columns).containsExactlyInAnyOrder(
                "step_id", "tenant_id", "session_id", "step_index",
                "step_type", "duration_ms");
        assertThat(columns).doesNotContain("created_at");
    }

    @Test
    void walModeIsEnabled() throws Exception {
        try (Connection conn = connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualToIgnoringCase("wal");
        }
    }

    @Test
    void sessionIndexExists() throws Exception {
        Set<String> indexes = new TreeSet<>();
        try (Connection conn = connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA index_list(step_log)")) {
            while (rs.next()) {
                indexes.add(rs.getString("name"));
            }
        }

        assertThat(indexes).contains("idx_step_log_tenant_session");
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }
}
