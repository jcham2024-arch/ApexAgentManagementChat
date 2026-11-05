# Agent Handoff — S2.1 Session Package

**From:** Architect agent
**To:** Build agent
**Session:** S2.1 — Port interfaces, records, PORT-CATALOGUE
**Prerequisite:** S2.0 merged to `main`, CI green, nine module skeletons exist

---

## Attached files

Attach the two files below in the next session:

1. **`PHASE-S2.1-PORTS.md`** — the session package (below, after "File to attach")
2. Nothing else. `PHASE-S2-KERNEL.md` is already in the repo.

---

## File to attach — `PHASE-S2.1-PORTS.md`

~~~markdown
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

### 4.1 `apex-chat/apex-chat-core/src/main/java/com/apex/chat/common/identity/TypedId.java`

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