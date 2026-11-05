---
bundle: apex-build-root
profile: 00-root-router
target_branch: main
file_count: 1
file_index:
  - APEX_BUILD.md
---

## Agent Instructions

Read this bundle. `file_count` is 1. Write `APEX_BUILD.md` at the
repository root, exactly as shown. Do not modify any other file.
Commit as `docs(agent): add root router and build contract`. Push to
`main`. Report the commit hash.

Then wait. Do not begin any phase. The phase files arrive in subsequent
sessions.

## Files

**APEX_BUILD.md**
```markdown
# APEX_BUILD.md — Agent Entry Point

**Version:** 1.0 · **Last updated:** 2026-01
**Read this file first, every session. Do not skip any section.**

## 0. What this file is

The root router. It carries the project scope, the hard rules, and the
load matrix. Build instructions arrive as separate phase files
(`PHASE-S1-FOUNDATION.md`, etc.), one per session. This file is the
contract those instructions must obey.

## 1. Session reading protocol

Read in this exact order:

1. This file (you are reading it).
2. `docs/apex-chat-spec/` — Layer 1 POJO spec (10 parts + File 11
   amendments). Read only the parts the current phase names in §7.
3. `docs/PORT-CATALOGUE.md` — the nine ports and their implementation
   registry.
4. `PHASE-S<N>-<NAME>.md` — the build instructions for this session.
5. `docs/INVARIANTS.md` — the testable claims the phase must satisfy.

Do not write code until all five are loaded. If any is missing, stop
and report which.

## 2. What Apex is

A multi-tenant, self-evolving multi-agent platform. Three layers:

- **Layer 1** — `apex-chat/*`: pure abstract POJOs. No I/O, no threads,
  no network, no third-party dependencies beyond JSpecify.
- **Layer 2** — the engine: `apex-core/*` (ports + planner + loop +
  tools + store interfaces) plus adapters (Spring AI, MCP Toolbox,
  SQLite, Postgres).
- **Layer 3** — the prompt stacks: governs how *you* build, not what
  you build.

Every capability is a **port** in the core, implemented by an
**adapter**. The core never imports an adapter.

## 3. The nine ports (the contract surface)

The core owns these interfaces. Every adapter implements exactly one.
Do not add a port without amending `docs/PORT-CATALOGUE.md`.

| Port | Purpose |
|---|---|
| `ChatModelPort` | Provider-agnostic inference |
| `PlannerPort` | Planning strategy (A*, Utility, Supervisor) |
| `ToolExecutorPort` | Tool execution |
| `ApprovalPort` | Human-in-the-loop approvals |
| `StoragePort` | Persistence (step, session, workspace) |
| `VectorStorePort` | Semantic retrieval |
| `SecretsPort` | Credentials, API keys |
| `AuditPort` | Chained audit log |
| `TelemetryPort` | Observability (spans, metrics) |

## 4. Three tiers (module boundaries)

| Tier | Modules | Allowed dependencies |
|---|---|---|
| **0 — Pure core** | `apex-chat-*`, `engine-core`, `engine-planner`, `engine-tools` (ports only), `engine-store` (ports only), `engine-improve` | `java.base`, `org.jspecify` |
| **1 — Local adapters** | `engine-tools` (impl), `engine-store-sqlite`, `engine-llm-http`, `apex-node` | Tier 0 + JDK IO + HTTP client |
| **2 — Enterprise adapters** | `adapter-spring-ai`, `adapter-mcp-toolbox`, `apex-platform/*`, `engine-server` | Tier 0 + Spring + Google + Jackson + Micrometer |

A Tier 0 module that imports Spring, Google, or Embabel is a build
failure, not a code review comment. A feature that *requires* Spring is
a Tier 2 feature; it goes into an adapter, never into the core.

## 5. Three enforcement gates

Every commit must pass all three. Failing any one blocks merge.

1. **Maven Enforcer** — `mvn enforcer:enforce` fails if any banned group
   ID reaches a Tier 0 module's compile classpath, transitively. The
   banned list includes `org.springframework*`, `org.embabel*`,
   `com.google.cloud*`, `com.google.mcp*`, `com.fasterxml.jackson*`,
   `io.micrometer*`.
2. **jdeps** — `jdeps apex-core-*.jar | grep -E 'springframework|
   embabel|google'` must return empty. This catches reflection and
   shaded dependencies.
3. **ArchUnit** — `CorePurityTest` fails on any enterprise import, any
   `java.io`/`java.nio.file`/`java.net`/`java.lang.reflect` import, and
   any direct use of `Executors.*` in a Tier 0 module.

## 6. Coding principles (hard rules)

### Documentation

- Every public class, interface, record, and method has Javadoc.
  Explain **why**, not what.
- Every package has `package-info.java` with `@NullMarked` and a
  one-paragraph purpose.
- Every Maven module has a `README.md`: purpose, tier, allowed
  dependencies, ports consumed or implemented, tests, one example.

### Naming and formatting

- Types `PascalCase`, methods/fields `camelCase`, constants
  `UPPER_SNAKE_CASE`, packages lowercase singular nouns.
- No abbreviations except industry-standard (`id`, `url`, `http`,
  `json`, `sql`).
- Records for value objects. Sealed interfaces for closed unions.
  Interfaces for extension.
- `List.copyOf` / `Map.copyOf` in every record compact constructor with
  a collection component.
- No `null` in records. No `Optional` in record components or
  parameters. `Optional` only as a return type.
- No `var` in public signatures.
- 4-space indent, 100-column soft cap, no tabs.

### Context discipline — the 200-line rule

- **Every source file is ≤ 200 lines.** Split when exceeded.
- **Every method is ≤ 40 lines.** Extract.
- **Every Javadoc block is ≤ 15 lines.** Move detail to the package
  `README.md`.
- **Every test class is ≤ 200 lines.** Split by behavior.

These are hard rules, not guidelines. A file over 200 lines is a
refactor ticket, not a merge candidate.

### Testing

- JUnit 5 + AssertJ + ArchUnit. jqwik for parametric invariants.
- Every public behavior has at least one test. Every invariant in
  `docs/INVARIANTS.md` has a named test.
- Integration tests tagged `@Tag("integration")`.
- Test the behavior, not the type: `PermissionGrantExpiryTest`, not
  `PermissionGrantTest`.

### Dependencies

- **No dependency may be added to a Tier 0 module.** Ever.
- A new adapter dependency is permitted only in Tier 1 or Tier 2, and
  only if listed in `docs/PORT-CATALOGUE.md`.
- **Commercial-license clean:** Apache 2.0, MIT, BSD, ISC only. No GPL,
  AGPL, SSPL, Commons Clause, even transitively. The license gate fails
  the build on any of these.

## 7. Phase load matrix

| Phase | Phase file | Also read |
|---|---|---|
| S1 — Foundation | `PHASE-S1-FOUNDATION.md` | Spec Parts 1–2 |
| S2 — Kernel | `PHASE-S2-KERNEL.md` | Spec Parts 3, 6, 8 |
| S3 — Security | `PHASE-S3-SECURITY.md` | Spec Parts 4, 9, 10 |
| S4 — Memory + Sandbox | `PHASE-S4-MEMORY-SANDBOX.md` | Spec Parts 5, 8 |
| S5 — Flywheel | `PHASE-S5-FLYWHEEL.md` | Spec File 11 |
| S6 — Server + GUI | `PHASE-S6-SERVER-GUI.md` | Spec Parts 6, 9 |
| S7 — Council | `PHASE-S7-COUNCIL.md` | Spec Part 5 (orchestration) |
| S8 — Node | `PHASE-S8-NODE.md` | Spec Part 9 (portability) |

**One phase per session.** Do not start the next phase until the human
says go. Do not scaffold modules from a future phase.

## 8. Commit and verification protocol

Every session:

1. Create branch `phase/<phase-id>-<slug>` from `main`.
2. Commit in logical units. Format: `<type>(<phase>): <subject>`.
   Types: `feat`, `fix`, `test`, `refactor`, `docs`, `build`, `ci`,
   `chore`. Subject imperative, ≤72 chars.
3. Push the branch. **Never commit to `main` directly.**
4. Report the CI run link and its status.
5. **Do not claim tests pass.** Say "CI status: green" or "CI status:
   red" with the link. The runtime cannot execute `mvn test` locally;
   CI is the verifier.
6. Stop when exit criteria are met. Report: files created (paths), test
   count, CI link, judgment calls, blockers.

## 9. Stop-and-report triggers

Stop and wait for human input when:

1. Phase exit criteria are met.
2. A rule in §6 would be violated by the requested change.
3. A phase file names a type, port, or invariant that does not exist in
   the POJO spec or `docs/PORT-CATALOGUE.md`.
4. A dependency not allowed by §4 is needed.
5. CI fails twice on the same root cause.
6. You find a bug in a prior phase — report, do not silently fix.

Do not stop for: variable names, log levels, test fixture data, import
ordering. Make a reasonable choice; note it in the commit body.

## 10. Tone

Terse. Factual. No emoji. No preamble. Report what you did, what CI
verified, what is blocked. If you made a judgment call, say so and why.

---

**End of router.** The next file you read is determined by §7.
```

## Commit Plan

1. `docs(agent): add root router and build contract`