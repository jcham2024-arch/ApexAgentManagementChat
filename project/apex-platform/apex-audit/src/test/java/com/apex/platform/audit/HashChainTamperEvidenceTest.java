package com.apex.platform.audit;

import com.apex.engine.core.port.model.AuditEntry;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chain-level tamper-evidence tests for the HMAC-SHA256 audit log.
 * {@link HashChainTest} already covers per-entry hash sensitivity and
 * short-key rejection; this class verifies that the assembled chain
 * detects modification, reordering, and missing records.
 */
@Tag("integration")
class HashChainTamperEvidenceTest {

    private static final byte[] KEY = key32();

    private static byte[] key32() {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) 0x42);
        return k;
    }

    private InMemoryAuditLog logWith(int count) {
        InMemoryAuditLog log = new InMemoryAuditLog(KEY);
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < count; i++) {
            log.append("action-" + i, at.plusSeconds(i));
        }
        return log;
    }

    @Test
    void correctChainVerifies() {
        InMemoryAuditLog log = logWith(5);

        assertThat(log.verifyChain()).isTrue();
    }

    @Test
    void modifyingPriorRecordInvalidatesChain() throws Exception {
        InMemoryAuditLog log = logWith(5);
        List<AuditEntry> entries = entriesOf(log);
        AuditEntry tampered = new AuditEntry(entries.get(1).id(), "TAMPERED",
                entries.get(1).at(), entries.get(1).previousHash(),
                entries.get(1).hash(), entries.get(1).traceId(),
                entries.get(1).spanId());
        List<AuditEntry> tamperedList = new ArrayList<>(entries);
        tamperedList.set(1, tampered);
        setEntries(log, tamperedList);

        assertThat(log.verifyChain()).isFalse();
    }

    @Test
    void reorderingRecordsInvalidatesChain() throws Exception {
        InMemoryAuditLog log = logWith(4);
        List<AuditEntry> entries = entriesOf(log);
        List<AuditEntry> reordered = new ArrayList<>(entries);
        Collections.swap(reordered, 0, 1);
        setEntries(log, reordered);

        assertThat(log.verifyChain()).isFalse();
    }

    @Test
    void missingRecordInvalidatesChain() throws Exception {
        InMemoryAuditLog log = logWith(4);
        List<AuditEntry> entries = entriesOf(log);
        List<AuditEntry> missing = new ArrayList<>(entries);
        missing.remove(1);
        setEntries(log, missing);

        assertThat(log.verifyChain()).isFalse();
    }

    @Test
    void truncatedChainVerifiesAsValidPrefix() throws Exception {
        // verifyChain walks each entry against its predecessor, so a
        // truncated chain is still a well-formed prefix and verifies.
        // Detecting truncation would require committing to a separate
        // head hash; InMemoryAuditLog does not track one.
        InMemoryAuditLog log = logWith(4);
        List<AuditEntry> entries = entriesOf(log);
        List<AuditEntry> truncated = new ArrayList<>(entries.subList(0, 2));
        setEntries(log, truncated);

        assertThat(log.verifyChain()).isTrue();
    }

    @Test
    void canonicalSerializationIsStable() {
        HashChain chain = new HashChain(KEY);
        String first = chain.compute("prev", "id-1", "transfer 10", 1000L,
                "trace-1", "span-1");
        String second = chain.compute("prev", "id-1", "transfer 10", 1000L,
                "trace-1", "span-1");

        assertThat(first).isEqualTo(second).isNotBlank();
        assertThat(chain.compute("prev", "id-2", "transfer 10", 1000L,
                "trace-1", "span-1")).isNotEqualTo(first);
        assertThat(chain.compute("prev", "id-1", "transfer 100", 1000L,
                "trace-1", "span-1")).isNotEqualTo(first);
        assertThat(chain.compute("prev2", "id-1", "transfer 10", 1000L,
                "trace-1", "span-1")).isNotEqualTo(first);
    }

    @Test
    void shortKeyRejected() {
        assertThatThrownBy(() -> new HashChain(new byte[16]))
                .isInstanceOf(AuditLogException.class)
                .satisfies(e -> assertThat(((AuditLogException) e).category())
                        .isEqualTo("config"));
    }

    @Test
    void nullKeyRejected() {
        assertThatThrownBy(() -> new HashChain(null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("unchecked")
    private static List<AuditEntry> entriesOf(InMemoryAuditLog log)
            throws Exception {
        Field field = InMemoryAuditLog.class.getDeclaredField("entries");
        field.setAccessible(true);
        return (List<AuditEntry>) field.get(log);
    }

    private static void setEntries(InMemoryAuditLog log,
                                   List<AuditEntry> entries) throws Exception {
        Field field = InMemoryAuditLog.class.getDeclaredField("entries");
        field.setAccessible(true);
        field.set(log, List.copyOf(entries));
    }
}
