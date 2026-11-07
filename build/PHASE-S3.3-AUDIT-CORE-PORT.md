# PHASE-S3.3-AUDIT-CORE-PORT.md — Session Package, Part 2 of 8

**Version:** 1.0 · **Part 2 of 8:** §4.1–§4.2, AuditEntry and AuditPort.
Parts: SESSION · CORE-PORT · AUDIT-CORE · AUDIT-LOG · CLI · AUDIT-TESTS ·
RUNNER-TESTS · CRITERIA, each `PHASE-S3.3-AUDIT-<part>.md`
(`AGENT_RULES.md` §2.13).

---
## 4. Files — `engine-core` (breaking changes)

### 4.1 `engine-core/src/main/java/com/apex/engine/core/port/model/AuditEntry.java`

Replace the entire file:

```java
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
 * {@code previousHash | id | action | at.toEpochMilli()} keyed by a
 * secret the caller does not control.
 */
public record AuditEntry(
        String id,
        String action,
        Instant at,
        String previousHash,
        String hash
) {
    public AuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(previousHash, "previousHash");
        Objects.requireNonNull(hash, "hash");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (hash.isBlank()) {
            throw new IllegalArgumentException("hash must not be blank");
        }
    }
}
```

### 4.2 `engine-core/src/main/java/com/apex/engine/core/port/AuditPort.java`

Replace the entire file:

```java
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
```
