package com.apex.platform.audit;

import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.model.AuditEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory {@link AuditPort}. Append-only, hash-chained, PII-redacted.
 * Not persisted across process restarts; file-backed persistence lands
 * in S6. The chain is verified by walking every entry from genesis.
 *
 * <p>Thread-safe for appends via a single internal lock; the audit
 * log is not on the hot path, so contention is not a concern. Reads
 * return an immutable snapshot.
 */
public final class InMemoryAuditLog implements AuditPort {

    private final HashChain chain;
    private final PiiRedactor redactor;
    private final List<AuditEntry> entries = new ArrayList<>();

    public InMemoryAuditLog(byte[] key) {
        this(new HashChain(key), new PiiRedactor());
    }

    public InMemoryAuditLog(HashChain chain, PiiRedactor redactor) {
        this.chain = Objects.requireNonNull(chain, "chain");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
    }

    @Override
    public synchronized AuditEntry append(String action, Instant at) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(at, "at");

        String redactedAction = redactor.redact(action);
        String id = UUID.randomUUID().toString();
        String previousHash = entries.isEmpty()
                ? ""
                : entries.get(entries.size() - 1).hash();
        String hash = chain.compute(previousHash, id, redactedAction,
                at.toEpochMilli());

        AuditEntry entry = new AuditEntry(id, redactedAction, at,
                previousHash, hash);
        entries.add(entry);
        return entry;
    }

    @Override
    public synchronized List<AuditEntry> all() {
        return List.copyOf(entries);
    }

    @Override
    public synchronized boolean verifyChain() {
        String expectedPrevious = "";
        for (AuditEntry entry : entries) {
            if (!expectedPrevious.equals(entry.previousHash())) {
                return false;
            }
            String recomputed = chain.compute(entry.previousHash(),
                    entry.id(), entry.action(), entry.at().toEpochMilli());
            if (!recomputed.equals(entry.hash())) {
                return false;
            }
            expectedPrevious = entry.hash();
        }
        return true;
    }
}
