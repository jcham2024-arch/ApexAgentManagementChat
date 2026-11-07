package com.apex.platform.audit;

import com.apex.engine.core.port.model.AuditEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryAuditLogTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final byte[] KEY = key();

    InMemoryAuditLog log;

    private static byte[] key() {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) 0x42);
        return k;
    }

    @BeforeEach
    void setUp() {
        log = new InMemoryAuditLog(KEY);
    }

    @Test
    void appendReturnsEntryWithComputedHash() {
        AuditEntry entry = log.append("login", BASE);

        assertThat(entry.hash()).isNotBlank();
        assertThat(entry.previousHash()).isEmpty();
        assertThat(entry.action()).isEqualTo("login");
        assertThat(entry.at()).isEqualTo(BASE);
    }

    @Test
    void secondEntryChainsToFirst() {
        AuditEntry first = log.append("login", BASE);
        AuditEntry second = log.append("logout", BASE.plusSeconds(1));

        assertThat(second.previousHash()).isEqualTo(first.hash());
    }

    @Test
    void allReturnsImmutableSnapshot() {
        log.append("login", BASE);
        List<AuditEntry> snapshot = log.all();

        assertThatThrownBy(() -> snapshot.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allReturnsInAppendOrder() {
        log.append("first", BASE);
        log.append("second", BASE.plusSeconds(1));
        log.append("third", BASE.plusSeconds(2));

        assertThat(log.all()).extracting(AuditEntry::action)
                .containsExactly("first", "second", "third");
    }

    @Test
    void verifyChainOnEmptyLogReturnsTrue() {
        assertThat(log.verifyChain()).isTrue();
        assertThat(log.all()).isEmpty();
    }

    @Test
    void verifyChainOnValidChainReturnsTrue() {
        log.append("one", BASE);
        log.append("two", BASE.plusSeconds(1));
        log.append("three", BASE.plusSeconds(2));

        assertThat(log.verifyChain()).isTrue();
    }

    @Test
    void verifyChainDetectsTamperedEntry() throws Exception {
        log.append("one", BASE);
        AuditEntry original = log.append("two", BASE.plusSeconds(1));
        log.append("three", BASE.plusSeconds(2));

        assertThat(log.verifyChain()).isTrue();

        // Tamper by replacing entry #2 in the internal list with a copy
        // that has a different action. This simulates external corruption
        // of the stored log.
        java.lang.reflect.Field entriesField =
                InMemoryAuditLog.class.getDeclaredField("entries");
        entriesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<AuditEntry> entries =
                (java.util.List<AuditEntry>) entriesField.get(log);

        AuditEntry tampered = new AuditEntry(
                original.id(), "TAMPERED", original.at(),
                original.previousHash(), original.hash(), "", "");
        entries.set(1, tampered);

        assertThat(log.verifyChain()).isFalse();
    }

    @Test
    void appendRedactsPii() {
        AuditEntry entry = log.append("login alice@example.com", BASE);

        assertThat(entry.action()).isEqualTo("login [EMAIL]");
        assertThat(log.verifyChain()).isTrue();
    }

    @Test
    void identicalRedactedInputsProduceTheSameHashShape() {
        AuditEntry first = log.append("mail alice@example.com", BASE);
        AuditEntry second = log.append("mail bob@example.com", BASE);
        HashChain chain = new HashChain(KEY);

        assertThat(first.action()).isEqualTo(second.action());

        String recomputed = chain.compute(first.previousHash(), first.id(),
                first.action(), first.at().toEpochMilli());
        assertThat(recomputed).isEqualTo(first.hash());
    }

    @Test
    void chainCannotBeForgedWithoutSecret() {
        byte[] otherKey = new byte[32];
        Arrays.fill(otherKey, (byte) 0x24);
        InMemoryAuditLog forged = new InMemoryAuditLog(otherKey);

        AuditEntry entry = forged.append("login", BASE);
        HashChain verifier = new HashChain(KEY);

        assertThat(verifier.compute(entry.previousHash(), entry.id(),
                entry.action(), entry.at().toEpochMilli()))
                .as("a different key must not reproduce the entry hash")
                .isNotEqualTo(entry.hash());
    }

    @Test
    void rejectsNullActionAndNullInstant() {
        assertThatThrownBy(() -> log.append(null, BASE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> log.append("login", null))
                .isInstanceOf(NullPointerException.class);
    }
}
