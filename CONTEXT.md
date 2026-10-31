# CONTEXT.md — Project Map & File Index

**Version:** 1.0 · **Always loaded. The wiki homepage.**

## 0. What this file is

The master index of every file that governs this build, plus the
project map. Read once at session start; consult during the phase when
the phase file says "see §N" or "read spec part X".

Two files are always loaded: `AGENT_RULES.md` (behavior) and this file
(context). Everything else is pointed to from §2.

**If a file is not listed in §2, it does not govern the build.** Do not
read it as a source of truth. If you need a resource not listed, stop
and report.

## 1. What Apex is

A multi-tenant, self-evolving multi-agent platform. Three layers:

- **Layer 1** — `apex-chat/*`: pure abstract POJOs. No I/O, no threads,
  no network, no third-party dependencies beyond JSpecify. Authoritative
  spec at `docs/apex-chat-spec/`.
- **Layer 2** — the engine: `apex-core/*` (ports, planner, loop, tools,
  store interfaces) plus adapters (Spring AI, MCP Toolbox, SQLite,
  Postgres).
- **Layer 3** — the prompt stacks: govern how the agent builds, not what
  it builds.

Every capability is a **port** in the core, implemented by an
**adapter**. The core never imports an adapter.

## 2. File index

### A. Always loaded (every session)

| File | Purpose | Lines |
|---|---|---|
| `AGENT_RULES.md` | Behavior contract | ~145 |
| `CONTEXT.md` | This file | ~185 |

### B. Layer 3 reference (load when the phase names them)

| File | Purpose | Load when |
|---|---|---|
| `STANDARDS.md` | Language coding standards | Implementing any module |
| `SALVAGE.md` | Prior-repo patterns to reimplement | Named by a phase |
| `docs/INVARIANTS.md` | Testable claims (K/E/C invariants) | Writing tests |
| `ROADMAP.md` | Delivery phases and versions | Orienting |

### C. Layer 1 POJO spec — `docs/apex-chat-spec/`

The authoritative domain model. Ten parts plus amendments. The phase
file names which parts are in scope; do not read all eleven unless the
phase says so.

| File | Covers |
|---|---|
| `01-charter-and-principles.md` | Charter, topology, conventions |
| `02-common-and-capability.md` | Common + capability modules |
| `03-core-model-content-message-thinking-prompt.md` | Model, content, message, thinking, prompt |
| `04-core-participant-thread-session-ui-guardrail.md` | Participant, thread, session, ui, guardrail |
| `05-core-chat-subsystems.md` | Memory, workspace, artifacts, resources, turn, concurrency, orchestration |
| `06-core-chat-container.md` | Chat aggregate, mutations, events, factories |
| `07-tools-module.md` | Tools module |
| `08-agent-module.md` | Agent module |
| `09-interaction-ownership-static-dynamic.md` | Interaction, ownership, static/dynamic, portability |
| `10-security-conventions-build-acceptance.md` | Security, conventions, build order |
| `11-amendments.md` | Tenant scoping, prune, cost, telemetry, projection, invariants |

### D. Build phase instructions — one file per session

| File | Phase |
|---|---|
| `PHASE-S1-FOUNDATION.md` | S1 — ports, POMs, gates |
| `PHASE-S2-KERNEL.md` | S2 — planner, loop, tools, CLI |
| `PHASE-S2.0-MODULE-SKELETONS-SESSION.md` | S2.0 — protocol, context deltas, scope |
| `PHASE-S2.0-MODULE-SKELETONS-POMS.md` | S2.0 — POM changes (§3.1–§3.5) |
| `PHASE-S2.0-MODULE-SKELETONS-MODULES.md` | S2.0 — module files, CI gate, exit criteria |
| `PHASE-S3-SECURITY.md` | S3 — auth, tenant, audit, HITL |
| `PHASE-S4-MEMORY-SANDBOX.md` | S4 — memory router, three sandbox tiers |
| `PHASE-S5-FLYWHEEL.md` | S5 — log → propose → gate → deploy |
| `PHASE-S6-SERVER-GUI.md` | S6 — SSE server, React chat MVP |
| `PHASE-S7-COUNCIL.md` | S7 — confidence gate, parallel solvers |
| `PHASE-S8-NODE.md` | S8 — A2A node, SBC/Android participant |

Supplemental phase files (`PHASE-S<N>-<NAME>-<SECTION>.md`) exist when a
phase exceeds 200 lines. The phase file names them.

**One phase file per session.** If the human provides two, stop and
report.

### E. Registries and contracts

| File | Purpose |
|---|---|
| `docs/PORT-CATALOGUE.md` | The nine ports and their Tier 1 / Tier 2 implementations |
| `docs/INVARIANTS.md` | Testable claims (see §B) |

## 3. The nine ports

The core owns these interfaces. Every adapter implements exactly one. Do
not add a port without amending `docs/PORT-CATALOGUE.md`.

| Port | Tier 1 impl | Tier 2 impl |
|---|---|---|
| `ChatModelPort` | `engine-llm-http` | `adapter-spring-ai` |
| `PlannerPort` | `engine-planner` (A*) | `adapter-spring-ai` (Supervisor) |
| `ToolExecutorPort` | `engine-tools` | `engine-tools-spring` |
| `ApprovalPort` | `engine-approval-cli` | `adapter-spring-ai` (SSE) |
| `StoragePort` | `engine-store-sqlite` | `adapter-spring-jdbc` |
| `VectorStorePort` | `engine-store-sqlite-vss` | `adapter-spring-ai` (pgvector) |
| `SecretsPort` | `SecretsPort-env` | `adapter-spring-vault` |
| `AuditPort` | `AuditPort-file` | `adapter-spring-audit` |
| `TelemetryPort` | `TelemetryPort-noop` | `adapter-micrometer` |

## 4. Three tiers

| Tier | Modules | Allowed dependencies |
|---|---|---|
| **0 — Pure core** | `apex-chat-*`, `engine-core`, `engine-planner`, `engine-tools` (ports), `engine-store` (ports), `engine-improve` | `java.base`, `org.jspecify` |
| **1 — Local adapters** | `engine-tools` (impl), `engine-store-sqlite`, `engine-llm-http`, `apex-node` | Tier 0 + JDK IO + HTTP |
| **2 — Enterprise adapters** | `adapter-spring-ai`, `adapter-mcp-toolbox`, `apex-platform/*`, `engine-server` | Tier 0 + Spring + Google + Jackson + Micrometer |

A feature that requires Spring is a Tier 2 feature. It goes into an
adapter, never into the core.

## 5. Three enforcement gates

Every commit must pass all three. Failing any blocks merge.

### Gate 1 — Maven Enforcer
Banned in Tier 0 (transitively): `org.springframework*`,
`com.google.cloud*`, `com.google.mcp*`, `org.embabel*`,
`com.fasterxml.jackson*`, `io.micrometer*`, `com.zaxxer*`. Config lives
in each Tier 0 module's `pom.xml`. Do not disable it.

### Gate 2 — jdeps
CI inspects `apex-core-*.jar` for the same banned packages via `jdeps`.
Catches reflection, `ServiceLoader`, shaded deps.

### Gate 3 — ArchUnit
`CorePurityTest` in each Tier 0 module fails on: enterprise imports,
`java.io` / `java.nio.file` / `java.net` / `java.lang.reflect` imports,
`Executors.*` usage. Runs in every `mvn test`.

## 6. Phase load matrix

| Phase | Phase file | Spec parts in scope |
|---|---|---|
| S1 | `PHASE-S1-FOUNDATION.md` | 01, 02 |
| S2 | `PHASE-S2-KERNEL.md` | 03, 06, 08 |
| S3 | `PHASE-S3-SECURITY.md` | 04, 09, 10 |
| S4 | `PHASE-S4-MEMORY-SANDBOX.md` | 05, 08 |
| S5 | `PHASE-S5-FLYWHEEL.md` | 11 |
| S6 | `PHASE-S6-SERVER-GUI.md` | 06, 09 |
| S7 | `PHASE-S7-COUNCIL.md` | 05 |
| S8 | `PHASE-S8-NODE.md` | 09 |

One phase per session. Do not start the next until the human says go.
Do not scaffold modules from a future phase.

## 7. Deployment targets

All three run the same Tier 0 core. Only the wired adapters differ.

| Target | Runtime | Storage | Model |
|---|---|---|---|
| **Cloud server** | JVM 21 + Spring Boot | Postgres + pgvector | HTTP, any OpenAI-compatible |
| **SBC swarm node** | JVM 21 CLI | SQLite | HTTP, OpenAI-compatible |
| **Android SDK node** | ART via Kotlin/Java | Room | LiteRT or HTTP |

The pure core (`apex-core-no-spring.jar`) is byte-identical across all
three. The Android SDK phase (S9, deferred) proves this.

## 8. Maintaining this index

This file is the master index. Every new file that governs the build
must be added to §2 with its purpose and load-when. Every new port must
be added to §3 and to `docs/PORT-CATALOGUE.md`. Every new phase must be
added to §6.

Keeping this file current is part of every phase's exit criteria. A
phase is not complete if it added a file that §2 does not list.

---

**End.** The next file is the phase file named in §6.
