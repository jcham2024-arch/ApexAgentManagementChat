/**
 * Durable, append-only SQLite-backed {@link com.apex.engine.core.port.AuditPort}.
 * Tier 1. Implements AuditPort using an embedded SQLite ledger; the HMAC-SHA256
 * hash chain is computed by {@link com.apex.platform.audit.HashChain} and PII
 * redaction by {@link com.apex.platform.audit.PiiRedactor}, both reused from
 * the existing apex-audit module.
 *
 * <p>Tier 0 (engine-core) sees only the {@code AuditPort} interface — no
 * SQLite, JDBC, SQL, or file I/O types leak into the core.
 */
@NullMarked
package com.apex.platform.audit.persistence.sqlite;

import org.jspecify.annotations.NullMarked;
