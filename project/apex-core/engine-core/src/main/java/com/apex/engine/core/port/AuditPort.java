package com.apex.engine.core.port;

import com.apex.engine.core.port.model.AuditEntry;

import java.time.Instant;
import java.util.List;

/**
 * Append-only, tamper-evident audit log. The port owns the hash chain
 * and the PII redaction. Callers provide an action string and a
 * timestamp; the port returns the entry it created.
 *
 * <p>Tier 1 impl: InMemoryAuditLog (S3.3, in-memory) plus
 * file-backed (S6). Tier 2 impl: adapter-spring-audit (S6+).
 */
public interface AuditPort {

    /**
     * Redacts PII from {@code action}, computes the chain hash, appends
     * the entry, and returns it. The redacted form is what gets stored.
     */
    AuditEntry append(String action, Instant at);

    /** Returns an immutable snapshot of all entries, in append order. */
    List<AuditEntry> all();

    /**
     * Recomputes every hash in the chain and verifies that each entry's
     * {@code previousHash} matches its predecessor's {@code hash}.
     * Returns {@code false} if any check fails.
     */
    boolean verifyChain();
}
