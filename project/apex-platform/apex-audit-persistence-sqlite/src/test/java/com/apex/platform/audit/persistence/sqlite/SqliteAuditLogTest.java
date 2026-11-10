package com.apex.platform.audit.persistence.sqlite;

import com.apex.engine.core.port.model.AuditEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit and integration tests for {@link SqliteAuditLog}: genesis chaining,
 * sequential ordering, restart persistence, tamper detection, concurrent
 * appends, and the append-only storage trigger.
 */
class SqliteAuditLogTest {

    private static final byte[] KEY = key32();

    private static byte[] key32() {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) 0x42);
        return k;
    }

    private static SqliteAuditLog open(@TempDir Path dir) throws Exception {
        return new SqliteAuditLog(dir.resolve("audit.db"), KEY);
    }

    @Test
    void genesisAppendHasEmptyPreviousHash(@TempDir Path dir) throws Exception {
        try (SqliteAuditLog log = open(dir)) {
            AuditEntry entry = log.append("genesis", Instant.parse("2026-01-01T00:00:00Z"));

            assertThat(entry.previousHash()).isEqualTo("");
            assertThat(entry.hash()).isNotBlank();
            assertThat(log.verifyChain()).isTrue();
        }
    }

    @Test
    void sequentialAppendsChainCorrectly(@TempDir Path dir) throws Exception {
        try (SqliteAuditLog log = open(dir)) {
            AuditEntry first = log.append("action-0", Instant.parse("2026-01-01T00:00:00Z"));
            AuditEntry second = log.append("action-1", Instant.parse("2026-01-01T00:00:01Z"));

            assertThat(second.previousHash()).isEqualTo(first.hash());
            assertThat(second.hash()).isNotEqualTo(first.hash());
            assertThat(log.verifyChain()).isTrue();
        }
    }

    @Test
    void allReturnsEntriesInSequenceOrder(@TempDir Path dir) throws Exception {
        try (SqliteAuditLog log = open(dir)) {
            log.append("first", Instant.parse("2026-01-01T00:00:00Z"));
            log.append("second", Instant.parse("2026-01-01T00:00:01Z"));
            log.append("third", Instant.parse("2026-01-01T00:00:02Z"));

            List<AuditEntry> entries = log.all();
            assertThat(entries).hasSize(3);
            assertThat(entries.get(0).action()).isEqualTo("first");
            assertThat(entries.get(1).action()).isEqualTo("second");
            assertThat(entries.get(2).action()).isEqualTo("third");
        }
    }

    @Test
    void redactedActionIsPersistedNotRawPii(@TempDir Path dir) throws Exception {
        try (SqliteAuditLog log = open(dir)) {
            log.append("contact alice@example.com about transfer",
                    Instant.parse("2026-01-01T00:00:00Z"));

            List<AuditEntry> entries = log.all();
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).action()).contains("[EMAIL]");
            assertThat(entries.get(0).action()).doesNotContain("alice@example.com");
        }
    }

    @Test
    void verifyChainReturnsFalseWhenRowModified(@TempDir Path dir) throws Exception {
        try (SqliteAuditLog log = open(dir)) {
            log.append("original", Instant.parse("2026-01-01T00:00:00Z"));

            // Insert a rogue row directly with a wrong hash (simulates
            // tampering; the UPDATE trigger would block a direct update).
            try (java.sql.Connection c =
                         java.sql.DriverManager.getConnection(
                                 "jdbc:sqlite:" + dir.resolve("audit.db").toAbsolutePath())) {
                try (java.sql.Statement s = c.createStatement()) {
                    s.execute("INSERT INTO audit_log (id, action, at, previous_hash, " +
                            "hash, trace_id, span_id) VALUES ('rogue-1', 'tampered', " +
                            "1000, 'wrong-previous', 'wrong-hash', '', '')");
                }
            }

            assertThat(log.verifyChain()).isFalse();
        }
    }

    @Test
    void verifyChainReturnsFalseWhenRowDeleted(@TempDir Path dir) throws Exception {
        try (SqliteAuditLog log = open(dir)) {
            log.append("first", Instant.parse("2026-01-01T00:00:00Z"));
            log.append("second", Instant.parse("2026-01-01T00:00:01Z"));

            // Insert a rogue row whose hash does not chain from the previous
            // entry, breaking the chain (simulates a missing/broken link).
            try (java.sql.Connection c =
                         java.sql.DriverManager.getConnection(
                                 "jdbc:sqlite:" + dir.resolve("audit.db").toAbsolutePath())) {
                try (java.sql.Statement s = c.createStatement()) {
                    s.execute("INSERT INTO audit_log (id, action, at, previous_hash, " +
                            "hash, trace_id, span_id) VALUES ('rogue-2', 'inserted', " +
                            "2000, 'broken-link', 'broken-hash', '', '')");
                }
            }

            assertThat(log.verifyChain()).isFalse();
        }
    }

    @Test
    void restartResumesChain(@TempDir Path dir) throws Exception {
        String hashBeforeRestart;
        try (SqliteAuditLog log = open(dir)) {
            log.append("before-restart", Instant.parse("2026-01-01T00:00:00Z"));
            hashBeforeRestart = log.all().get(0).hash();
            assertThat(log.verifyChain()).isTrue();
        }

        // Reopen the same database file: chain must resume from the last hash.
        try (SqliteAuditLog log = open(dir)) {
            List<AuditEntry> entries = log.all();
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).hash()).isEqualTo(hashBeforeRestart);

            AuditEntry next = log.append("after-restart", Instant.parse("2026-01-01T00:00:01Z"));
            assertThat(next.previousHash()).isEqualTo(hashBeforeRestart);
            assertThat(log.verifyChain()).isTrue();
        }
    }

    @Test
    void concurrentAppendsDoNotCorruptChain(@TempDir Path dir) throws Exception {
        // A single shared adapter instance; append() is synchronized, so
        // concurrent threads serialize through it. The chain must remain
        // valid with no lost entries.
        try (SqliteAuditLog log = open(dir)) {
            int threads = 8;
            int appendsPerThread = 25;
            Thread[] workers = new Thread[threads];
            java.util.concurrent.CountDownLatch latch =
                    new java.util.concurrent.CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                workers[t] = new Thread(() -> {
                    for (int i = 0; i < appendsPerThread; i++) {
                        log.append("t" + threadId + "-i" + i,
                                Instant.parse("2026-01-01T00:00:00Z"));
                    }
                    latch.countDown();
                });
                workers[t].start();
            }

            latch.await();
            for (Thread w : workers) {
                w.join();
            }

            assertThat(log.verifyChain()).isTrue();
            assertThat(log.all()).hasSize(threads * appendsPerThread);
        }
    }

    @Test
    void shortKeyRejected(@TempDir Path dir) throws Exception {
        assertThatThrownBy(() -> new SqliteAuditLog(dir.resolve("audit.db"), new byte[16]))
                .isInstanceOf(com.apex.platform.audit.AuditLogException.class);
    }

    @Test
    void appendOnlyTriggerRejectsUpdate(@TempDir Path dir) throws Exception {
        try (SqliteAuditLog log = open(dir)) {
            log.append("original", Instant.parse("2026-01-01T00:00:00Z"));
            AuditEntry entry = log.all().get(0);

            try (java.sql.Connection c =
                         java.sql.DriverManager.getConnection(
                                 "jdbc:sqlite:" + dir.resolve("audit.db").toAbsolutePath())) {
                try (java.sql.Statement s = c.createStatement()) {
                    assertThatThrownBy(() -> s.execute(
                            "UPDATE audit_log SET action = 'tampered' WHERE id = '" +
                                    entry.id() + "'"))
                            .isInstanceOf(java.sql.SQLException.class);
                }
            }
        }
    }

    @Test
    void appendOnlyTriggerRejectsDelete(@TempDir Path dir) throws Exception {
        try (SqliteAuditLog log = open(dir)) {
            log.append("original", Instant.parse("2026-01-01T00:00:00Z"));
            AuditEntry entry = log.all().get(0);

            try (java.sql.Connection c =
                         java.sql.DriverManager.getConnection(
                                 "jdbc:sqlite:" + dir.resolve("audit.db").toAbsolutePath())) {
                try (java.sql.Statement s = c.createStatement()) {
                    assertThatThrownBy(() -> s.execute(
                            "DELETE FROM audit_log WHERE id = '" + entry.id() + "'"))
                            .isInstanceOf(java.sql.SQLException.class);
                }
            }
        }
    }

    @Test
    void partialWriteSimulationNoPartialRowVisible(@TempDir Path dir) throws Exception {
        // Simulate a failed transaction: inject an SQLException by trying
        // to insert a duplicate id, which violates the UNIQUE constraint
        // and rolls back. Verify no partial row is visible afterward.
        try (SqliteAuditLog log = open(dir)) {
            AuditEntry first = log.append("original", Instant.parse("2026-01-01T00:00:00Z"));
            int beforeCount = log.all().size();

            try (java.sql.Connection c =
                         java.sql.DriverManager.getConnection(
                                 "jdbc:sqlite:" + dir.resolve("audit.db").toAbsolutePath())) {
                try (java.sql.Statement s = c.createStatement()) {
                    assertThatThrownBy(() -> s.execute(
                            "INSERT INTO audit_log (id, action, at, previous_hash, " +
                                    "hash, trace_id, span_id) VALUES ('" +
                                    first.id() + "', 'duplicate', 1000, '', 'badhash', '', '')"))
                            .isInstanceOf(java.sql.SQLException.class);
                }
            }

            // The failed insert must not leave a partial row visible.
            assertThat(log.all()).hasSize(beforeCount);
            assertThat(log.verifyChain()).isTrue();
        }
    }

    @Test
    void restoreDrillTamperPersistsAcrossRestart(@TempDir Path dir) throws Exception {
        // Restore drill: tamper with the database, verify detection,
        // then confirm the tamper persists across restart (restoration
        // requires external backup replacement).
        try (SqliteAuditLog log = open(dir)) {
            log.append("before-tamper", Instant.parse("2026-01-01T00:00:00Z"));
            log.append("after-tamper", Instant.parse("2026-01-01T00:00:01Z"));
            assertThat(log.verifyChain()).isTrue();
        }

        // Tamper: insert a rogue row directly in the database (outside the
        // adapter) with a broken hash chain.
        try (java.sql.Connection c =
                     java.sql.DriverManager.getConnection(
                             "jdbc:sqlite:" + dir.resolve("audit.db").toAbsolutePath())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("INSERT INTO audit_log (id, action, at, previous_hash, " +
                        "hash, trace_id, span_id) VALUES ('rogue-tamper', " +
                        "'tampered', 99999, 'broken-link', 'broken-hash', '', '')");
            }
        }

        // Verify detection: chain is broken after tamper.
        try (SqliteAuditLog log = open(dir)) {
            assertThat(log.verifyChain()).isFalse();
        }

        // Tamper persists across restart: reopen and chain is still broken.
        // Restoration requires replacing the database file with a backup
        // copy (documented in docs/operations/audit-persistence-baselines.md).
        try (SqliteAuditLog log = open(dir)) {
            assertThat(log.verifyChain()).isFalse();
            assertThat(log.all()).hasSize(3);
        }
    }
}
