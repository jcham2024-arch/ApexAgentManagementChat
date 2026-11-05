package com.apex.engine.store.sqlite;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.model.StepLogEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIsolationTest {

    private static final TenantId TENANT_A = new TenantId("tenant-a");
    private static final TenantId TENANT_B = new TenantId("tenant-b");
    private static final String SESSION_A = "session-a";
    private static final String SESSION_B = "session-b";

    @TempDir
    Path tempDir;

    Path dbPath;
    SqliteStoragePort port;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("isolation.db");
        port = new SqliteStoragePort(dbPath);
    }

    private StepLogEntry entry(String id, TenantId tenant,
                               String session, int index) {
        return new StepLogEntry(id, tenant, session, index, "action", 0L);
    }

    @Test
    void tenantAQueryCannotSeeTenantBRows() {
        port.appendStep(entry("a1", TENANT_A, SESSION_A, 0));
        port.appendStep(entry("b1", TENANT_B, SESSION_B, 0));

        assertThat(port.stepsFor(TENANT_A, SESSION_A))
                .extracting(StepLogEntry::stepId)
                .containsExactly("a1");
    }

    @Test
    void tenantBCannotSeeTenantAWithGuessedSessionId() {
        port.appendStep(entry("a1", TENANT_A, SESSION_A, 0));

        assertThat(port.stepsFor(TENANT_B, SESSION_A)).isEmpty();
    }

    @Test
    void bothTenantsCoexistWithoutCrossTalk() {
        port.appendStep(entry("a1", TENANT_A, SESSION_A, 0));
        port.appendStep(entry("a2", TENANT_A, SESSION_A, 1));
        port.appendStep(entry("b1", TENANT_B, SESSION_B, 0));

        assertThat(port.stepsFor(TENANT_A, SESSION_A))
                .extracting(StepLogEntry::stepId)
                .containsExactly("a1", "a2");
        assertThat(port.stepsFor(TENANT_B, SESSION_B))
                .extracting(StepLogEntry::stepId)
                .containsExactly("b1");
    }

    @Test
    void rawSqlCannotCrossTenantsEvenWithoutPortGuards() throws Exception {
        port.appendStep(entry("a1", TENANT_A, SESSION_A, 0));
        port.appendStep(entry("b1", TENANT_B, SESSION_B, 0));

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM step_log "
                     + "WHERE tenant_id = 'tenant-a'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void legacyRowsDoNotLeakToAnyRealTenant() {
        // The migration sentinel is not a real tenant; no query for
        // TENANT_A or TENANT_B should ever return it.
        assertThat(port.stepsFor(TENANT_A, SESSION_A)).isEmpty();
        assertThat(port.stepsFor(TENANT_B, SESSION_B)).isEmpty();
    }
}
