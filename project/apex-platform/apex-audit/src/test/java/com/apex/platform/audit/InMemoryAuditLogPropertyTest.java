package com.apex.platform.audit;

import com.apex.engine.core.port.model.AuditEntry;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the in-memory audit log. Appending entries
 * and verifying the chain must always pass for unmodified logs. Any
 * tampering with entries (modified action) changes the recomputed hash,
 * which means verifyChain() would fail. Entries forged under a
 * different key must never verify against this key.
 */
class InMemoryAuditLogPropertyTest {

    private static final byte[] KEY = key();

    private static byte[] key() {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) 0x42);
        return k;
    }

    @Property
    void tamperedActionChangesHash(@ForAll String action1,
                                   @ForAll String action2,
                                   @ForAll String tampered) {
        if (action1.equals(action2) || action1.equals(tampered) || action2.equals(tampered)) {
            return;
        }

        InMemoryAuditLog log = new InMemoryAuditLog(KEY);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        AuditEntry first = log.append(action1, base);
        AuditEntry second = log.append(action2, base.plusSeconds(1));

        HashChain chain = new HashChain(KEY);
        String expectedHash = chain.compute(
                second.previousHash(), second.id(), tampered, second.at().toEpochMilli());
        assertThat(expectedHash).isNotEqualTo(second.hash());
    }

    @Property
    void differentKeyCannotVerify(@ForAll String action) {
        InMemoryAuditLog log = new InMemoryAuditLog(KEY);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        log.append(action, base);

        byte[] otherKey = new byte[32];
        Arrays.fill(otherKey, (byte) 0x24);
        HashChain otherChain = new HashChain(otherKey);

        AuditEntry entry = log.all().get(0);
        String forged = otherChain.compute(
                entry.previousHash(), entry.id(), entry.action(), entry.at().toEpochMilli());
        assertThat(forged).isNotEqualTo(entry.hash());
    }
}
