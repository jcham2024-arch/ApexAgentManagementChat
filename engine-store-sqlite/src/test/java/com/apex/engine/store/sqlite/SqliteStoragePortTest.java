package com.apex.engine.store.sqlite;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.model.StepLogEntry;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteStoragePortTest {

    @TempDir
    Path tempDir;

    Path dbPath;
    SqliteStoragePort port;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test.db");
        port = new SqliteStoragePort(dbPath);
    }

    private static final TenantId TENANT = new TenantId("t-test");

    static StepLogEntry entry(String stepId, String sessionId, int index) {
        return new StepLogEntry(stepId, TENANT, sessionId, index, "plan",
                index * 10L);
    }

    @Test
    void appendAndQueryRoundTrip() {
        StepLogEntry written = entry("s-0", "session-a", 0);

        port.appendStep(written);

        assertThat(port.stepsFor(TENANT, "session-a")).containsExactly(written);
    }

    @Test
    void queryOrdersByStepIndex() {
        port.appendStep(entry("s-2", "session-a", 2));
        port.appendStep(entry("s-0", "session-a", 0));
        port.appendStep(entry("s-1", "session-a", 1));

        List<StepLogEntry> entries = port.stepsFor(TENANT, "session-a");

        assertThat(entries).extracting(StepLogEntry::index)
                .containsExactly(0, 1, 2);
    }

    @Test
    void queryFiltersBySession() {
        port.appendStep(entry("s-a", "a", 0));
        port.appendStep(entry("s-b", "b", 0));

        List<StepLogEntry> entries = port.stepsFor(TENANT, "a");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).sessionId()).isEqualTo("a");
    }

    @Test
    void queryEmptySessionReturnsEmpty() {
        assertThat(port.stepsFor(TENANT, "never-seen")).isEmpty();
    }

    @Test
    void duplicateStepIdThrows() {
        StepLogEntry written = entry("s-0", "session-a", 0);
        port.appendStep(written);

        assertThatThrownBy(() -> port.appendStep(written))
                .isInstanceOf(StorageException.class)
                .satisfies(e -> assertThat(((StorageException) e).category())
                        .isEqualTo("duplicate"));
    }

    @Test
    void persistenceAcrossReopen() {
        StepLogEntry written = entry("s-0", "session-a", 0);
        port.appendStep(written);

        SqliteStoragePort reopened = new SqliteStoragePort(dbPath);

        assertThat(reopened.stepsFor(TENANT, "session-a")).containsExactly(written);
    }

    @Test
    void rejectsNullEntryAndNullSession() {
        assertThatThrownBy(() -> port.appendStep(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> port.stepsFor(null, "s"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> port.stepsFor(TENANT, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void tenantIdColumnExists() throws Exception {
        boolean found = false;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(step_log)")) {
            while (rs.next()) {
                if ("tenant_id".equalsIgnoreCase(rs.getString("name"))) {
                    found = true;
                }
            }
        }

        assertThat(found).isTrue();
    }

    @Test
    void oldSchemaMigratesAndLegacyRowsGetTheSentinel() throws Exception {
        Path legacy = tempDir.resolve("legacy.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + legacy);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE step_log ("
                    + "step_id TEXT PRIMARY KEY, session_id TEXT NOT NULL,"
                    + " step_index INTEGER NOT NULL, step_type TEXT NOT NULL,"
                    + " duration_ms INTEGER NOT NULL)");
            st.execute("INSERT INTO step_log VALUES ('old-1','s',0,'plan',0)");
        }

        SqliteStoragePort migrated = new SqliteStoragePort(legacy);

        assertThat(migrated.stepsFor(new TenantId("legacy-migration"), "s"))
                .extracting(StepLogEntry::stepId)
                .containsExactly("old-1");
        assertThat(migrated.stepsFor(TENANT, "s"))
                .as("legacy rows must be unreachable for a real tenant")
                .isEmpty();
    }
}
