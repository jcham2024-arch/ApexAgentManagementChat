# PHASE-S2.1-PORTS-RECORDS.md — Session Package, Part 3 of 4

**Version:** 1.0 · **Sub-phase of S2** · **Part 3 of 4:** §4.3–§4.4,
records and `package-info.java`.

Parts: `PHASE-S2.1-PORTS-SESSION.md` (§0–§4.1: protocol, context deltas,
prerequisite, scope, `TypedId`) ·
`PHASE-S2.1-PORTS-INTERFACES.md` (§4.2) · this file (§4.3–§4.4) ·
`PHASE-S2.1-PORTS-TESTS-DOCS.md` (§4.5–§7). Split for the 200-line rule
in `AGENT_RULES.md` §2.13.

---

### 4.3 Thirteen supporting records

Package `com.apex.engine.core.port.model`.

Every record follows this pattern:
- `Objects.requireNonNull` on every reference field
- `List.copyOf` on every collection field
- Numeric fields validated ≥ 0 where nonsensical otherwise
- No `null` values permitted anywhere

Provide each record fully. The first is written in full; follow the
pattern for the remaining twelve based on the field descriptions.

**ChatRequest.java**
```java
package com.apex.engine.core.port.model;

import java.util.List;
import java.util.Objects;

/**
 * A single chat request. Prompt parts are preassembled by the caller;
 * the port does not chunk, template, or fragment.
 */
public record ChatRequest(String modelId, List<String> promptParts) {
    public ChatRequest {
        Objects.requireNonNull(modelId, "modelId");
        promptParts = List.copyOf(promptParts);
    }
}
```

**ChatResponse.java** — fields `String content`; content non-null.

**PlanRequest.java** — fields `String goalId,
List<String> availableActionIds`; goalId non-null, list copied.

**PlanResult.java** — fields `List<String> chosenActionIds`; list
copied.

**ToolCall.java** — fields `String id, String toolName,
String argumentsJson`; all non-null. Arguments are raw JSON; parsing is
the tool executor's job.

**ToolResult.java** — fields `String callId, boolean isError,
String output, long durationMs`; callId and output non-null,
durationMs ≥ 0.

**ApprovalRequest.java** — fields `String id, String summary,
String riskLevel`; all non-null. `riskLevel` is one of `L0`–`L5` per
`CONTEXT.md` §3 (defined fully in S3; here it is a plain string).

**ApprovalDecision.java** — fields `String requestId,
boolean approved, String comment`; requestId non-null, comment defaults
to `""` if null.

**StepLogEntry.java** — fields `String stepId, String sessionId,
int index, String stepType, long durationMs`; IDs and stepType
non-null, index ≥ 0, durationMs ≥ 0.

**VectorHit.java** — fields `String id, double score`; id non-null.

**SecretRef.java** — fields `String name`; non-null, non-blank.

**AuditEntry.java** — fields `String id, String action,
java.time.Instant at, String hash`; all non-null.

**TelemetrySpan.java** — fields `String name, long durationMs`; name
non-null, durationMs ≥ 0.

### 4.4 `package-info.java` files

**`com.apex.engine.core.port/package-info.java`**
```java
/**
 * The nine port interfaces owned by the core. Every adapter implements
 * exactly one. See {@code docs/PORT-CATALOGUE.md} for the registry.
 * Tier 0.
 */
@NullMarked
package com.apex.engine.core.port;

import org.jspecify.annotations.NullMarked;
```

**`com.apex.engine.core.port.model/package-info.java`**
```java
/**
 * Records supporting the nine ports. Immutable value objects. No
 * behavior beyond compact-constructor validation. Tier 0.
 */
@NullMarked
package com.apex.engine.core.port.model;

import org.jspecify.annotations.NullMarked;
```

**End of part 3.** Continue in `PHASE-S2.1-PORTS-TESTS-DOCS.md`.
