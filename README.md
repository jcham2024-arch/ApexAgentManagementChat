main
# Apex Agent Management Chat (LLM Architecture & Context Guide)

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

## Current State & Areas for Improvement
When asked to write new features or refactor, prioritize these areas:
1. Testing: The architectural rules are enforced by ArchUnit, but unit test coverage for the deterministic planner and the cryptographic audit log is currently sparse. Adding robust tests is a high priority.
2. Documentation: The root README.md is missing. Keep code comments and Javadoc up to date, especially for Port interfaces.
3. Avoid Over-Engineering: The tiered structure is powerful but complex. Do not add new modules unless absolutely necessary. 

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
  platform-android/ Tier 1: Android SDK node (ART + Room, deferred)
  platform-desktop/ Tier 1: desktop entry point
  testkit/          Shared test utilities
```

## Build

Java 21, Maven 3.9+. The root reactor aggregates the three tier group
parents plus adapter modules:

```bash
mvn install                 # build + test all modules, install to ~/.m2
mvn -f project/apex/pom.xml test   # engine reactor tests only
```

Local builds need workaround flags (see `docs/LOCAL-BUILD.md`):

```bash
mvn install -Dmaven.repo.local=$PWD/.m2 \
  -DargLine="-Djava.io.tmpdir=$HARNESS_SCRATCH/apex-tmp"
```

### Enforcement gates

Every commit must pass three gates, enforced mechanically:

1. **Maven Enforcer** — banned enterprise deps in Tier 0 (Spring, Google,
   Embabel, Jackson, Micrometer, HikariCP), transitively.
2. **jdeps bytecode purity** — CI inspects Tier 0 jars for banned packages.
3. **ArchUnit** — every Tier 0 module has a `CorePurityTest` proving no
   `java.io`, `java.nio.file`, `java.net`, reflection, `Runtime`,
   `ProcessBuilder`, `System.*`, or `Executors.*`.

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
