main
# Apex Agent Management Chat (LLM Architecture & Context Guide)

![CI](https://github.com/jcham2024-arch/ApexAgentManagementChat/actions/workflows/ci.yml/badge.svg)

## Project Overview
Apex Agent Management Chat is a multi-module, Java 21 project designed to orchestrate autonomous AI agents. It strictly adheres to Hexagonal Architecture (Ports and Adapters) and a tiered module structure to ensure the core business logic remains completely isolated from external frameworks and infrastructure. This document is optimized to provide immediate, structured context for LLM agents contributing to or navigating this codebase.

## Build System & Tooling
- Build Tool: Maven
- Java Version: Java 21
- Testing Frameworks: JUnit 5, ArchUnit (for enforcing architectural boundaries)
- Null-Safety: JSpecify (using @NullMarked at the package level)

## The Tiered Architecture (CRITICAL CONSTRAINT)
The project enforces strict boundaries between modules using the Maven Enforcer plugin. When modifying dependencies, you must respect these tiers.

### Tier 0: The Pure Core
- Modules: apex-core, engine-core, engine-planner, engine-tools, engine-store
- Rule: NO third-party infrastructure dependencies. The build will fail if Spring, Jackson, Micrometer, or similar frameworks are introduced here.
- Purpose: Pure, deterministic, highly testable business logic, interfaces (ports), and domain models.

### Tier 2: Platform Services (Adapters)
- Modules: apex-auth, apex-audit
- Rule: Allowed to use external libraries (e.g., jjwt for JWT, standard cryptographic libraries).
- Purpose: Provide concrete implementations (adapters) for the interfaces defined in Tier 0.

## Module Breakdown
- apex-core: Defines fundamental domain models and port interfaces (e.g., AuditPort).
- engine-core: The central loop for agent execution (e.g., PlanLoop) and orchestration.
- engine-planner: Contains the AStarPlanner. This planner is strictly deterministic (uses a sequence counter to break ties in priority queues).
- engine-tools: Implementations of tools the agents can use (e.g., file I/O, HTTP calls).
- apex-auth: JWT authentication and authorization. Uses the modern jjwt (0.12.6) library. Enforces HS256, validates exp, sub, tenant_id, and roles.
- apex-audit: Provides a tamper-evident audit trail using an HMAC-SHA256 HashChain. Requires a minimum key length of 32 bytes.

## Coding Standards & Design Patterns
- Hexagonal Architecture: Always separate ports (interfaces in Tier 0) from adapters (implementations in Tier 2).
- Immutability: Favor Java Records for data carriers (e.g., the Node record in AStarPlanner).
- Determinism: AI agents and planners must be deterministic. Avoid random number generation in the core engine. Ensure collections are ordered or sorted predictably.
- Null Safety: All packages must have a package-info.java annotated with @NullMarked. Do not return null; use Optional or explicit empty collections.
- Resource Management: Long-running tasks must respect the CancellationToken passed through the PlanLoop. Check for cancellation at every step.

## Current State

Documentation: `AGENT_RULES.md` (behavior contract), `CONTEXT.md` (project map
and file index), `docs/LOCAL-BUILD.md` (Maven workaround flags), and `build/`
(phase-gated session files).

Test coverage: `AStarPlannerTest` and `AStarPlannerDeterminismTest` cover
planner determinism; `PlanLoopTest` covers loop success, cancellation, stuck,
failed, and max-steps behavior; `HashChainTest` covers per-entry HMAC
sensitivity and short-key rejection; `CorePurityTest` enforces Tier 0 purity
in every Tier 0 module.

Status: S2 kernel (ports, A* planner, plan loop, tools, SQLite storage, CLI)
landed on `main`. This session is a housekeeping/cleanup pass with no active
phase file.

Avoid over-engineering: the tiered structure is powerful but complex; do not
add new modules unless absolutely necessary.

## Instructions for LLM Agents Working on this Repo
1. Before adding a dependency to Tier 0, check if it violates the Maven Enforcer constraints in the root pom.xml.
2. If modifying the AStarPlanner, ensure the priority queue logic remains deterministic.
3. If adding a new capability (like a new agent tool), define the interface in Tier 0 (engine-tools) and the implementation in the appropriate adapter module.
4. Ensure all new code compiles under Java 21. 
5. Do not use nested code fences in generated markdown. Use standard indentation for code snippets if necessary, or inline code formatting.

# ApexAgentManagementChat

A from-scratch, architecture-first agent orchestration engine built on Java 21.
It is **not** a wrapper around Spring AI or LangChain4j.

## Architectural thesis

Apex targets the regulated, Java-standardized enterprise segment where
deterministic behavior and auditability are functional requirements, not
nice-to-haves. Its wedge is architectural rigor for high-stakes workflows:

- **Deterministic-by-design orchestration.** A pure A* planner with
  sequence-counter tie-breaking. Same inputs produce the same plan, every
  time, across runs and across JVM restarts. No randomness in the core.
- **Cryptographic auditability.** A tamper-evident HMAC-SHA256 hash chain
  (minimum 32-byte key) records every state mutation. Compliance officers
  can re-run a session and verify that the same plan emerges.
- **Tiered hexagonal architecture.** The core (Tier 0) has zero
  infrastructure dependencies — no Spring, Jackson, Micrometer, or Google
  libraries. Every capability is a port in the core, implemented by an
  adapter. Adapters never leak into the core.

## Project structure

```
project/
  apex-core/        Tier 0 group parent: engine-core, engine-planner,
                    engine-tools, engine-store (ports + primitives)
  apex-chat/        Layer 1: apex-chat-core, apex-chat-domain,
                    apex-chat-store-sqlite (pure POJOs)
  apex-platform/    Tier 2 group parent: apex-auth, apex-tenant,
                    apex-audit, apex-app-real
  engine-cli/       Tier 1: task-file loader, ReAct runner, CLI entrypoint
  engine-llm-http/  Tier 1: OpenAI-compatible ChatModelPort adapter
  engine-json/      Tier 0: minimal JSON parser
  engine-memory/    Tier 0: episodic memory, context compactor, router
  engine-sandbox/   Tier 1: sandboxed tool execution tiers
  engine-store-sqlite/Tier 1: SQLite StoragePort
  engine-tools-impl/Tier 1: five kernel tools (read, write, list, http, run)
```

## Module map

| Boundary | Module | Role |
|---|---|---|
| Tier 0 pure core | `project/apex-core/engine-core` | PlanLoop, policy engine, core ports |
| Tier 0 pure core | `project/apex-core/engine-planner` | AStarPlanner |
| Tier 0 pure core | `project/apex-core/engine-tools` | Tool scaffolding (package-only shell; port interfaces live in engine-core/port) |
| Tier 0 pure core | `project/apex-core/engine-store` | StoragePort / CheckpointPort interfaces |
| Tier 0 pure core | `project/engine-json` | JSON scaffolding (package-only shell; parser TBD) |
| Tier 0 pure core | `project/engine-memory` | Memory scaffolding (package-only shell; router/compactor TBD) |
| Tier 0 Layer 1 | `project/apex-chat/apex-chat-core` | Pure abstract POJOs |
| Tier 0 Layer 1 | `project/apex-chat/apex-chat-domain` | TypedId, TenantId identities |
| Tier 1 adapter | `project/engine-cli` | Task-file loader, ReAct runner, CLI entrypoint |
| Tier 1 adapter | `project/engine-llm-http` | OpenAI-compatible ChatModelPort adapter |
| Tier 1 adapter | `project/engine-sandbox` | Sandboxed tool execution tiers |
| Tier 1 adapter | `project/engine-store-sqlite` | SQLite StoragePort |
| Tier 1 adapter | `project/engine-tools-impl` | Five kernel tools (read/write/list/http/run) |
| Tier 2 platform | `project/apex-platform/apex-auth` | JWT auth, RBAC policy |
| Tier 2 platform | `project/apex-platform/apex-tenant` | Tenancy scaffolding (package-only shell; isolation TBD) |
| Tier 2 platform | `project/apex-platform/apex-audit` | HMAC-SHA256 hash chain, audit log |
| Tier 2 platform | `project/apex-platform/apex-app-real` | Real app entry point |
| Aggregators | `project/apex-chat`, `project/apex-core`, `project/apex-platform` | Group-parent POMs |
| Aggregators | `project/apex` | Engine reactor POM |
| Aggregators | `pom.xml` (root), `project/bom` | Root reactor / BOM |


## Build and Test

Maven is the single canonical build system. There is exactly one documented
build path: the root `pom.xml` reactor. Gradle files under `project/core-engine`
are an abandoned experiment and were removed; they are not a supported build
path.

Two entry points:

```bash
# Full tree: root reactor (group parents + adapters).
mvn -B install                                   # CI: full build + test

# Engine reactor only (all tiers under project/).
mvn -B -f project/apex/pom.xml verify            # canonical verification command
```

The engine reactor excludes four modules that live only in the root
reactor: `engine-otel`, `engine-metrics`, `engine-approval`,
`engine-eval`. Use the root `mvn install` to build and test those too.

Local builds need workaround flags (see `docs/LOCAL-BUILD.md`) because
`user.home` points at a Termux path and the default `java.io.tmpdir` does not
exist for the app UID:

```bash
export MAVEN_REPO_LOCAL=$PWD/.m2
export HARNESS_TMP=$HARNESS_SCRATCH/apex-tmp
mvn -B install -Dmaven.repo.local=$MAVEN_REPO_LOCAL \
  -DargLine="-Djava.io.tmpdir=$HARNESS_TMP"
```

`mvn test` cannot run locally without both flags; CI remains the authoritative
verifier (see `AGENT_RULES.md` §5).

### Enforcement gates

Every commit must pass three gates, enforced mechanically:

1. **Maven Enforcer** — banned enterprise deps in Tier 0 (Spring, Google,
   Embabel, Jackson, Micrometer, HikariCP), transitively.
2. **jdeps bytecode purity** — CI inspects Tier 0 jars for banned packages.
3. **ArchUnit** — every Tier 0 module has a `CorePurityTest` proving no
   `java.io`, `java.nio.file`, `java.net`, reflection, `Runtime`,
   `ProcessBuilder`, `System.*`, or `Executors.*`.

## Coverage visibility

Coverage is reported through CI test execution; no arbitrary thresholds are
enforced initially — visibility guides expansion. Critical modules with
explicit coverage tracking:

- Planner determinism: `AStarPlannerTest`, `AStarPlannerDeterminismTest`
- PlanLoop orchestration: `PlanLoopTest`, `PlanLoopIntegrationTest`
- Audit-chain verification: `HashChainTest`, `HashChainTamperEvidenceTest`
- Core purity constraints: `CorePurityTest` in every Tier 0 module

A coverage tool (e.g. JaCoCo) is not bound to the build because its license
(EPL/LGPL) is outside the project's allowed set (Apache 2.0, MIT, BSD, ISC);
adopting one requires a license-policy exception.

## Getting Started

Prerequisites:

- Java 21 (JDK)
- Maven 3.9+

Build and test the full tree:

```bash
export MAVEN_REPO_LOCAL=$PWD/.m2
export HARNESS_TMP=$HARNESS_SCRATCH/apex-tmp
mvn -B install -Dmaven.repo.local=$MAVEN_REPO_LOCAL \
  -DargLine="-Djava.io.tmpdir=$HARNESS_TMP"
```

`mvn test` cannot run locally without both flags (see `docs/LOCAL-BUILD.md`);
CI remains the authoritative verifier (see `AGENT_RULES.md` §5).

## Testing Strategy

Three tiers of automated testing:

- Unit tests — pure Tier 0 logic (planner, ports, domain models) plus
  `CorePurityTest` in every Tier 0 module enforcing architectural purity.
- Integration tests — `PlanLoopIntegrationTest` (checkpoint/resume, audit
  emission, cross-run determinism) and `HashChainTamperEvidenceTest`
  (tamper detection, reorder, missing records).
- Architectural tests — `CorePurityTest` (ArchUnit) and the CI jdeps
  bytecode-purity gate (Gate G2). Violations fail the build.

## Roadmap

Phase-gated delivery. One phase per session; phase files live in `build/`.

| Phase | Focus |
|-------|-------|
| S1 | Foundation |
| S2 | Kernel: ports, A* planner, plan loop, tools, SQLite storage, CLI |
| S3 | Security: JWT auth, tenancy, audit chain, HITL approval |
| S4 | Memory/sandbox: router, compactor, three sandbox tiers |
| S5 | Flywheel: log → propose → gate → deploy |
| S6 | Server/GUI: SSE server, React chat MVP |
| S7 | Council: confidence gate, parallel solvers |
| S8 | Node: A2A protocol, SBC/Android participant |

## Differentiators

| Capability | Apex | LangChain4j | Spring AI | Camunda |
|------------|------|-------------|-----------|---------|
| Deterministic planner | Native (A*) | No | No | Partial (BPMN) |
| Cryptographic audit chain | Native (HMAC-SHA256) | No | No | Add-on |
| Tier 0 purity (no infra in core) | Yes | No | No | No |
| JVM-native, air-gapped ready | Yes | Partial | Partial | No |
| Deterministic replay + plan preview | Yes | No | No | No |

## Contributing

Read `AGENT_RULES.md` and `CONTEXT.md` before writing code. Every phase
has a `build/PHASE-S<N>-<NAME>.md` with granular acceptance criteria.
Commit format: `<type>(<phase>): <subject>` with `Refs: S<n>`.
phase/s4.2-router-compactor
