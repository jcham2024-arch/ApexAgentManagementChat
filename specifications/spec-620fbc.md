# PHASE-S2.1-PORTS.md

**Version:** 1.0 · **Sub-phase of S2** · **Ends with all nine ports compiling and tested**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S2-KERNEL.md` (repo — router)
4. **This file**

Do not begin until all four are read. Do not read any spec parts.
Router §4 confirms S2.1 does not need the spec.

## 1. Context notes — deltas for S2.1

These are authoritative. Where they conflict with `CONTEXT.md`, they
win. Do not stop over them.

### 1.1 Port record location

All nine port interfaces live in `apex-core/engine-core` at package
`com.apex.engine.core.port`. All supporting records live in the same
module at `com.apex.engine.core.port.model`.

Tier 1 modules (`engine-tools-impl`, `engine-llm-http`,
`engine-store-sqlite`, `engine-cli`) implement these ports in later
sub-phases. In S2.1 they have no content yet — that is expected.

### 1.2 Port signatures are minimal

These are skeleton contracts. Streaming, cancellation, and event-driven
approval are added in later sub-phases as **additive** interface
methods (default methods where possible to avoid breaking existing
implementations). Do not preempt that work here.

### 1.3 `TypedId` may or may not exist

S1.1 created `TenantId` only. If `com.apex.chat.common.identity.TypedId`
does not exist, create it here. If it does exist, skip that file. Do
not modify it if present.

### 1.4 No spec, no factories, no DI

S2.1 defines interfaces and records only. No factory classes, no
service loaders, no Spring, no constructor injection. Adapters land in
S2.2–S2.6.

### 1.5 Approvals are synchronous in S2.1

`ApprovalPort.request` blocks until a decision is returned. The CLI
implementation (S2.6) will prompt the terminal. Event-driven approval
(SSE, Android notification) lands in S3. Document this in the port's
Javadoc; do not implement the async variant now.

### 1.6 Multi-method ports need anonymous classes in tests

`ChatModelPort` and `PlannerPort` and `ToolExecutorPort` and
`ApprovalPort` and `SecretsPort` and `TelemetryPort` are single-method
interfaces — they can be implemented with a lambda. `StoragePort`,
`VectorStorePort`, and `AuditPort` have two methods each — they need an
anonymous class. `PortTest` must handle both cases correctly.

## 2. Prerequisite

S2.0 merged to `main`. All eight module skeletons exist and build.
`mvn -B test` is green on `main`.

If any is false, stop and report.

## 3. Scope

Deliver these, nothing else:

1. `TypedId` in `apex-chat-common` (only if absent)
2. Nine port interfaces in `engine-core`
3. Thirteen supporting records in `engine-core.port.model`
4. `PortTest` — one test class proving all nine ports are implementable
5. `PortRecordsTest` — one test class covering every record's
   invariants
6. `docs/PORT-CATALOGUE.md` — the port registry
7. Update `engine-core` `package-info.java` and `README.md` to reflect
   that it now defines the nine ports

## 4. Files

All paths are relative to repo root.

### 4.1 `apex-chat/apex-chat-common/src/main/java/com/apex/chat/common/identity/TypedId.java`

Only if `TypedId` does not exist. Content:

```java
package com.apex.chat.common.identity;

import java.util.Objects;

/**
 * A type-tagged identifier. Prevents raw {@code String} identity
 * throughout Layer 1.
 *
 * @param <T> the type this identifier refers to
 */
public record TypedId<T>(String value) {
    public TypedId {
        Objects.requireNonNull(value, "TypedId value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TypedId must not be blank");
        }
    }
}
```

Add `TypedIdTest` alongside it with tests for: accepts non-blank,
rejects null, rejects blank, equality is value-based.

### 4.2 Nine port interfaces

Package `com.apex.engine.core.port`. Each interface has Javadoc naming
its Tier 1 implementation, its Tier 2 implementation (per
`CONTEXT.md` §3), and the sub-phase where its first implementation
lands. One method each except `StoragePort`, `VectorStorePort`, and
`AuditPort` which have two.

**ChatModelPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;

/**
 * Provider-agnostic chat inference. Tier 1 impl: engine-llm-http
 * (S2.4). Tier 2 impl: adapter-spring-ai (S3+). First impl lands in
 * S2.4. Streaming is added in S2.4 as an additive interface method.
 */
public interface ChatModelPort {
    ChatResponse chat(ChatRequest request);
}
```

**PlannerPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.PlanRequest;
import com.apex.engine.core.port.model.PlanResult;

/**
 * Planning strategy over typed actions. Tier 1 impl: engine-planner
 * (S2.2). First impl lands in S2.2.
 */
public interface PlannerPort {
    PlanResult plan(PlanRequest request);
}
```

**ToolExecutorPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;

/**
 * Executes a tool call and returns its result. Tier 1 impl:
 * engine-tools-impl (S2.3). First impl lands in S2.3.
 */
public interface ToolExecutorPort {
    ToolResult execute(ToolCall call);
}
```

**ApprovalPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;

/**
 * Synchronous human-in-the-loop approval. Tier 1 impl: engine-cli
 * (S2.6, terminal prompt). Tier 2 impl: adapter-spring-ai (S3, SSE).
 * Event-driven approval lands in S3 as an additive interface.
 */
public interface ApprovalPort {
    ApprovalDecision request(ApprovalRequest request);
}
```

**StoragePort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.StepLogEntry;

import java.util.List;

/**
 * Persistence for step and session logs. Tier 1 impl:
 * engine-store-sqlite (S2.5). Tier 2 impl: adapter-spring-jdbc (S6+).
 */
public interface StoragePort {
    void appendStep(StepLogEntry entry);
    List<StepLogEntry> stepsFor(String sessionId);
}
```

**VectorStorePort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.VectorHit;

import java.util.List;

/**
 * Semantic retrieval over embeddings. Tier 1 impl:
 * engine-store-sqlite (S4). Tier 2 impl: adapter-spring-ai (pgvector,
 * S4). First impl lands in S4.
 */
public interface VectorStorePort {
    void store(String id, float[] vector);
    List<VectorHit> search(float[] query, int k);
}
```

**SecretsPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.SecretRef;

/**
 * Credentials and API keys. Tier 1 impl: environment variables (S3).
 * Tier 2 impl: adapter-spring-vault (S3+). First impl lands in S3.
 */
public interface SecretsPort {
    String get(SecretRef ref);
}
```

**AuditPort.java**
```java
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
```

**TelemetryPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.TelemetrySpan;

/**
 * Observability spans. Tier 1 impl: no-op (S2). Tier 2 impl:
 * adapter-micrometer (S6+). First impl lands in S2 with a no-op.
 */
public interface TelemetryPort {
    void record(TelemetrySpan span);
}
```

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

### 4.5 Tests

**`engine-core/src/test/java/com/apex/engine/core/port/PortTest.java`**

One test class proving all nine ports are implementable. For
single-method ports, use a lambda. For multi-method ports, use an
anonymous class.

Example for `ChatModelPort` (single-method):

```java
@Test
void chatModelPortIsImplementable() {
    ChatModelPort port = req -> new ChatResponse("ok");
    assertThat(port.chat(new ChatRequest("m", List.of("hi"))).content())
            .isEqualTo("ok");
}
```

Example for `StoragePort` (two-method):

```java
@Test
void storagePortIsImplementable() {
    StoragePort port = new StoragePort() {
        @Override public void appendStep(StepLogEntry entry) { }
        @Override public List<StepLogEntry> stepsFor(String sessionId) {
            return List.of();
        }
    };
    assertThat(port.stepsFor("s")).isEmpty();
}
```

Repeat for all nine ports. One `@Test` per port. Class stays well under
200 lines.

**`engine-core/src/test/java/com/apex/engine/core/port/model/PortRecordsTest.java`**

One test class covering invariants of all thirteen records. For each
record:

- At least one `@Test` proving non-null component rejection via
  `assertThatNullPointerException`
- At least one `@Test` proving collection immutability via
  `UnsupportedOperationException` on returned list mutation (only for
  records with list components)
- At least one `@Test` proving numeric validation where applicable

Class stays well under 200 lines. If it grows beyond, split into
`PortRecordsTest` and `PortRecordsValidationTest`.

### 4.6 `docs/PORT-CATALOGUE.md`

```markdown
# PORT-CATALOGUE.md — The Nine Ports

The core owns these interfaces. Every adapter implements exactly one.
The core never imports an adapter. Tiers are defined in
`CONTEXT.md` §3.

| Port | Tier 1 impl | Tier 2 impl | First impl lands |
|---|---|---|---|
| `ChatModelPort` | `engine-llm-http` | `adapter-spring-ai` | S2.4 |
| `PlannerPort` | `engine-planner` | `adapter-spring-ai` | S2.2 |
| `ToolExecutorPort` | `engine-tools-impl` | (n/a — Tier 1 only) | S2.3 |
| `ApprovalPort` | `engine-cli` (terminal) | `adapter-spring-ai` (SSE) | S2.6 |
| `StoragePort` | `engine-store-sqlite` | `adapter-spring-jdbc` | S2.5 |
| `VectorStorePort` | `engine-store-sqlite` | `adapter-spring-ai` | S4 |
| `SecretsPort` | env vars | `adapter-spring-vault` | S3 |
| `AuditPort` | append-only file | `adapter-spring-audit` | S3 |
| `TelemetryPort` | no-op | `adapter-micrometer` | S2 (no-op) |

## Adding a port

1. Define the interface in `engine-core/.../port`.
2. Add supporting records to `engine-core/.../port/model`.
3. Add a row to this table with both Tier 1 and Tier 2 impls.
4. Add the port to `CONTEXT.md` §3.
5. Add a `@Test` to `PortTest` proving it is implementable.

## Adding an adapter

1. Create the module (Tier 1 or Tier 2 per `CONTEXT.md` §3).
2. Implement exactly one port.
3. Add the module to `CONTEXT.md` §4.
4. Add it to `PORT-CATALOGUE.md` under the port it implements.
```

### 4.7 Update `engine-core` metadata

**`engine-core/src/main/java/com/apex/engine/core/package-info.java`**
— replace content with:

```java
/**
 * Core loop, agent state machine, and the nine port interfaces.
 * Tier 0. Ports defined: all nine (see port subpackage).
 */
@NullMarked
package com.apex.engine.core;

import org.jspecify.annotations.NullMarked;
```

**`engine-core/README.md`** — update:

```markdown
# engine-core

**Tier:** 0 · **Purpose:** Core loop, agent state machine, and the nine
port interfaces.

## Allowed dependencies
java.base + jspecify only

## Ports consumed
none (defined here)

## Ports implemented
none (interfaces only — implementations live in Tier 1/Tier 2 modules)

## Ports defined
All nine. See `com.apex.engine.core.port` and `docs/PORT-CATALOGUE.md`.

## Tests
`PortTest`, `PortRecordsTest`
```

## 5. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] Nine port interfaces exist in `com.apex.engine.core.port`.
- [ ] Thirteen records exist in `com.apex.engine.core.port.model`.
- [ ] `TypedId` exists in `apex-chat-common` (either pre-existing or
      created here).
- [ ] `PortTest` has one `@Test` per port (nine total).
- [ ] `PortRecordsTest` covers non-null, collection immutability, and
  numeric validation for every record.
- [ ] `docs/PORT-CATALOGUE.md` exists with the table above.
- [ ] `engine-core/package-info.java` and `README.md` reflect the new
  ports.
- [ ] No file exceeds 200 lines.
- [ ] No enterprise dependency anywhere.
- [ ] No adapter implementation exists yet (S2.2+).
- [ ] No spec files created.
- [ ] All Tier 0 ArchUnit gates still pass.

## 6. Do NOT add

- Any implementation of any port
- Any adapter module
- Any factory, service loader, or DI
- Any spec file
- Any test that exercises behavior — records have none beyond
  validation
- Any dependency beyond JSpecify, JUnit 5, AssertJ
- Any file not named in §4

## 7. Commit

Three commits on `phase/s2.1-ports`:

1. `feat(S2.1): add TypedId to apex-chat-common` (skip if present)
2. `feat(S2.1): add nine port interfaces and thirteen port records`
3. `docs(S2.1): add PORT-CATALOGUE and update engine-core metadata`

Push branch. Report CI link and confirmation of each exit criterion
from §5. Do not report "S2.1 complete" without the CI link. If CI
fails twice on the same root cause, stop and report the log.

---

**End of S2.1 session package.**