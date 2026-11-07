package com.apex.engine.core.port.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A single entry in an append-only, hash-chained audit log. Each entry
 * commits to its predecessor's hash via {@link #previousHash}, so any
 * alteration to any entry breaks the chain from that point forward.
 *
 * <p>Genesis entries carry {@code previousHash = ""} (empty string, not
 * null). The {@code hash} field is an HMAC-SHA256 of
 * {@code previousHash | id | action | at.toEpochMilli() | traceId | spanId}
 * keyed by a secret the caller does not control. {@code traceId} and
 * {@code spanId} let an auditor traverse from an audit entry to its trace
 * and back without trusting the tracing backend.
 */
public record AuditEntry(
        String id,
        String action,
        Instant at,
        String previousHash,
        String hash,
        String traceId,
        String spanId) {
    public AuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(previousHash, "previousHash");
        Objects.requireNonNull(hash, "hash");
        traceId = traceId == null ? "" : traceId;
        spanId = spanId == null ? "" : spanId;
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (hash.isBlank()) {
            throw new IllegalArgumentException("hash must not be blank");
        }
    }
}
