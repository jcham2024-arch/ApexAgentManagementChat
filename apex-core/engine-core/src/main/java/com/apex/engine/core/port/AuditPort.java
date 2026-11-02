package com.apex.engine.core.port;

import com.apex.engine.core.port.model.AuditEntry;

import java.util.List;

/**
 * Tamper-evident audit log. Tier 1 impl: append-only file (S3). Tier 2
 * impl: adapter-spring-audit (S3+). First impl lands in S3.
 */
public interface AuditPort {
    void append(AuditEntry entry);
    List<AuditEntry> all();
}
