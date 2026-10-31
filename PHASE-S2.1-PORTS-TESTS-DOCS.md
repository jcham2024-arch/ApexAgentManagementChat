# PHASE-S2.1-PORTS-TESTS-DOCS.md — Session Package, Part 4 of 4

**Version:** 1.0 · **Sub-phase of S2** · **Part 4 of 4:** tests,
catalogue, metadata, exit criteria, commits.

Parts: `PHASE-S2.1-PORTS-SESSION.md` (§0–§4.1: protocol, context deltas,
prerequisite, scope, `TypedId`) ·
`PHASE-S2.1-PORTS-INTERFACES.md` (§4.2) ·
`PHASE-S2.1-PORTS-RECORDS.md` (§4.3–§4.4) · this file (§4.5–§7). Split
for the 200-line rule in `AGENT_RULES.md` §2.13.

---

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
