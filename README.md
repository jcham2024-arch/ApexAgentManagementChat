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
