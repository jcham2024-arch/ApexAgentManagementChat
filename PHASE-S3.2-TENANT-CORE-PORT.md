# PHASE-S3.2-TENANT-CORE-PORT.md — Session Package, Part 2 of 7

**Version:** 1.0 · **Part 2 of 7:** §4.1–§4.2, StepLogEntry and StoragePort.
Parts: SESSION · CORE-PORT · SQLITE · POMS-RUNNER · CLI · TESTS ·
ISOLATION-CRITERIA, each `PHASE-S3.2-TENANT-<part>.md` (`AGENT_RULES.md`
§2.13).

---
### 4.1 `engine-core/src/main/java/com/apex/engine/core/port/model/StepLogEntry.java`

Replace the entire file with:

```java
package com.apex.engine.core.port.model;

import com.apex.chat.common.identity.TenantId;

import java.util.Objects;

/**
 * A single step record. {@code tenantId} is required and must match
 * the tenant of any store it is appended to. {@code sessionId} is a
 * raw string for now (assigned by the CLI as a UUID); typed session
 * IDs land in S6.
 */
public record StepLogEntry(
        String stepId,
        TenantId tenantId,
        String sessionId,
        int index,
        String stepType,
        long durationMs
) {
    public StepLogEntry {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(stepType, "stepType");
        if (index < 0) throw new IllegalArgumentException("index < 0");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs < 0");
        if (stepId.isBlank()) {
            throw new IllegalArgumentException("stepId must not be blank");
        }
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (stepType.isBlank()) {
            throw new IllegalArgumentException("stepType must not be blank");
        }
    }
}
```

### 4.2 `engine-core/src/main/java/com/apex/engine/core/port/StoragePort.java`

Replace with:

```java
package com.apex.engine.core.port;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.model.StepLogEntry;

import java.util.List;

/**
 * Persistence for step and session logs. Every read is tenant-scoped:
 * {@link #stepsFor(TenantId, String)} returns only rows whose
 * {@code tenantId} matches the argument. Tier 1 impl:
 * engine-store-sqlite (S2.5). Tier 2 impl: adapter-spring-jdbc (S6+).
 */
public interface StoragePort {
    void appendStep(StepLogEntry entry);
    List<StepLogEntry> stepsFor(TenantId tenantId, String sessionId);
}
```
