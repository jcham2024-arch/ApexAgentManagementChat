# AGENTS.md — How to work on Apex Agent Management Chat

Read this file before doing anything. It is an entrypoint into the
project's own governance documents; it does not replace them.

## 1. Session reading protocol (mandatory, in order)

Do not write code until at least the first four are loaded.

1. `AGENT_RULES.md` — the behavior contract. Hard prohibitions, coding
   rules, commit format, verification protocol, stop triggers.
2. `CONTEXT.md` — project map and authoritative file index (§2).
3. `build/PHASE-S<N>-<NAME>.md` — the phase file for this session.
   **One phase file per session.** If the human provides two, stop and
   report. Split phases list their supplemental parts, e.g.
   `build/PHASE-S2.2-PLANNER-SESSION.md` + `-PRIMITIVES.md` +
   `-ASTAR.md` + `-LOOP.md` + `-TESTS-DOCS.md`.
4. The spec parts named in the phase file (`docs/apex-chat-spec/`).
5. `docs/INVARIANTS.md` — testable claims, when writing tests.
6. `docs/PORT-CATALOGUE.md` — port registry, when touching a port.

If any of 1–4 is missing, stop and report which.

## 2. What the project is

Multi-tenant, self-evolving multi-agent platform. Multi-module Maven,
Java 21. Hexagonal architecture: every capability is a **port** in the
core, implemented by an **adapter**; the core never imports an adapter.

- **Tier 0 (pure core)** — `project/apex-core/*`, `project/apex-chat/*`,
  `engine-json`. No third-party infrastructure dependencies, no `java.io`,
  `java.nio.file`, `java.net`, reflection, `Runtime`, `ProcessBuilder`,
  `System.*`, or `Executors.*`. Enforced by Maven Enforcer, jdeps, and
  per-module `CorePurityTest` (ArchUnit).
- **Tier 1** — adapters: `engine-llm-http`, `engine-cli`,
  `engine-store-sqlite`, `engine-tools-impl`, `engine-memory`,
  `engine-sandbox`, `platform-android`, `platform-desktop`, `testkit`.
- **Tier 2 (platform)** — `project/apex-platform/*`: auth, tenant,
  audit, real app. External libraries allowed here only.

Current code state lives under `project/` (reorganized from repo root —
older guides may show top-level modules; `project/` is authoritative).

## 3. Build and verify

Local `mvn` needs two workaround flags (see `docs/LOCAL-BUILD.md`):

```bash
mvn -B install -Dmaven.repo.local=$PWD/.m2 \
  -DargLine="-Djava.io.tmpdir=$HARNESS_SCRATCH/apex-tmp"
mvn -B test -Dmaven.repo.local=$PWD/.m2 \
  -DargLine="-Djava.io.tmpdir=$HARNESS_SCRATCH/apex-tmp" \
  -f project/apex/pom.xml
```

Do not add `project/apex` to the root reactor POM: it re-aggregates the
same directories and Maven fails with `DuplicateProjectException`.

Never claim tests pass. Say "CI status: green/red" with the run link —
CI is the authoritative verifier (AGENT_RULES §5).

## 4. Hard rules you must never break

Full list in AGENT_RULES §2. The ones agents violate most:

- No file over 200 lines; no method over 40 lines; no Javadoc over 15
  lines. Over-limit is a refactor, not a merge candidate.
- No enterprise dependency in Tier 0 (Spring, Google, Embabel, Jackson,
  Micrometer, HikariCP), not even transitively.
- No placeholder bodies: no `return null`, `TODO`,
  `UnsupportedOperationException`. No disabled or skipped tests.
- No mutable collection in a record without `List.copyOf` / `Map.copyOf`
  in the compact constructor. No `Optional` in record components or
  parameters. No `null` fields without JSpecify `@Nullable`.
- No commit to `main`. Branch per phase: `phase/s<N>-<name>`.
- No dependency on a future phase; do not scaffold what the phase file
  does not name.
- Licenses allowed: Apache 2.0, MIT, BSD, ISC only.

## 5. Commit format

```
<type>(<phase>): <subject>

Refs: S<n>
```

Types: `feat`, `fix`, `test`, `refactor`, `docs`, `build`, `ci`,
`chore`. Subject imperative, ≤72 chars.

## 6. Stop-and-report triggers

Stop and wait for human input when:

- Phase exit criteria are met (report files, test count, CI link,
  judgment calls, blockers).
- A requested change would violate AGENT_RULES §2.
- A phase file names a type, port, or invariant missing from the POJO
  spec or `docs/PORT-CATALOGUE.md`.
- A disallowed dependency is needed.
- CI fails twice on the same root cause.
- You find a bug in a prior phase — report, do not silently fix.

Do not stop for variable names, log levels, fixture data, or import
order; decide and note it in the commit body.

## 7. Where everything is

| Path | Contents |
|---|---|
| `project/` | All Maven modules (see CONTEXT.md and module READMEs) |
| `build/` | Phase files (the session's instructions) |
| `docs/apex-chat-spec/` | Authoritative Layer 1 POJO spec |
| `docs/PORT-CATALOGUE.md` | The nine ports and their implementations |
| `docs/INVARIANTS.md` | Named testable invariants |
| `docs/adr/` | Architecture decisions |
| `docs/LOCAL-BUILD.md` | Local Maven flags and gotchas |
| `STANDARDS.md`, `ROADMAP.md` | Layer 3 references, load when a phase names them |

If a file is not indexed in `CONTEXT.md` §2, it does not govern the
build — do not treat it as a source of truth.
