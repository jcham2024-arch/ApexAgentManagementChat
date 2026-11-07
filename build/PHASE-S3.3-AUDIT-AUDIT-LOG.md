# PHASE-S3.3-AUDIT-AUDIT-LOG.md — Session Package, Part 4 of 8

**Version:** 1.0 · **Part 4 of 8:** §5.4–§5.5, InMemoryAuditLog and module metadata.
Parts: SESSION · CORE-PORT · AUDIT-CORE · AUDIT-LOG · CLI · AUDIT-TESTS ·
RUNNER-TESTS · CRITERIA, each `PHASE-S3.3-AUDIT-<part>.md`
(`AGENT_RULES.md` §2.13).

---
### 5.4 `InMemoryAuditLog.java`

```java
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
```

### 5.5 Update `apex-audit` metadata

**`apex-platform/apex-audit/src/main/java/com/apex/platform/audit/package-info.java`** — replace:

```java
/**
 * Chained audit log with PII redaction. Tier 2. Implements AuditPort.
 * Hash chain uses HMAC-SHA256 over
 * previousHash|id|action|atMillis. In-memory only in S3.3;
 * file-backed persistence lands in S6.
 */
@NullMarked
package com.apex.platform.audit;

import org.jspecify.annotations.NullMarked;
```

**`apex-platform/apex-audit/README.md`** — replace:

```markdown
# apex-audit

**Tier:** 2 · **Purpose:** Chained audit log with PII redaction.

## Allowed dependencies
any (Tier 2); currently java.base + javax.crypto (from JRE) only

## Ports consumed
none

## Ports implemented
AuditPort (via InMemoryAuditLog)

## Chain format
Each entry commits to its predecessor via HMAC-SHA256 over
`previousHash|id|action|atMillis`. Genesis entries carry
`previousHash = ""`. Any alteration to any entry breaks the chain from
that point forward. Verification walks the chain from genesis and
recomputes every hash.

## PII redaction
Applied before hashing. Fixed-string replacement markers:
- `[EMAIL]`, `[PHONE]`, `[SSN]`, `[AWS_KEY]`, `[GITHUB_TOKEN]`,
  `[API_KEY]`, `[PEM_BLOCK]`

Identical redacted inputs produce identical hashes.

## Not implemented in S3.3
- File-backed persistence (S6)
- Monotonic timestamp enforcement (S6)
- Multi-writer coordination (S6)

## Tests
HashChainTest · PiiRedactorTest · InMemoryAuditLogTest
```
