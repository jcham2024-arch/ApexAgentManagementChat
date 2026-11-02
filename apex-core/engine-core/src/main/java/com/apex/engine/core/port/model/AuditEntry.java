package com.apex.engine.core.port.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One tamper-evident audit record. {@code hash} chains this entry to
 * the previous one.
 */
public record AuditEntry(String id, String action, Instant at, String hash) {
    public AuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(hash, "hash");
    }
}
