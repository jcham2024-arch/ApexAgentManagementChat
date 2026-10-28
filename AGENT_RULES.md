# AGENT_RULES.md — Apex Core Rules

> Hard cap: 200 lines. Everything else lives in `docs/supplements/` and is
> loaded per the matrix in §2. Do not skip the matrix.

## 1. Session Protocol

Every session, before any code, load in this order:

1. This file (you are reading it).
2. The supplements listed for your current phase in §2.
3. `docs/PHASES.md` §<current phase> — the execution spec.
4. `docs/INVARIANTS.md` — the testable claims you must satisfy.
5. `docs/ROADMAP.md` — phase context and exit criteria.

Do not start the phase until all five are loaded. If any is missing,
stop and report.

## 2. Load Matrix (phase → required supplements)

| Phase | Load these supplements |
|---|---|
| P1 | `STANDARDS.md` · `STRUCTURE.md` · `SALVAGE.md` (CancellationToken only) |
| P2 | `STANDARDS.md` · `STRUCTURE.md` |
| P3 | `STANDARDS.md` · `STRUCTURE.md` |
| P4 | `STANDARDS.md` · `STRUCTURE.md` · `SALVAGE.md` (RealPathJail, ProcessTreeTerminator) |
| P5 | `STANDARDS.md` · `STRUCTURE.md` · `SALVAGE.md` (tiered SKILL.md limits) |
| P6 | `STANDARDS.md` · `STRUCTURE.md` · `SALVAGE.md` (TokenEstimator, ContextCompactor, AgentEngine) |
| P7 | `STANDARDS.md` · `STRUCTURE.md` |
| P8 | `STANDARDS.md` · `STRUCTURE.md` |
| P9 | `STANDARDS.md` · `STRUCTURE.md` |
| P10 | `STANDARDS.md` · `STRUCTURE.md` |

`STANDARDS.md` and `STRUCTURE.md` are loaded once at P1 and stay loaded
for every subsequent phase. `SALVAGE.md` is loaded only when the matrix
lists it for your phase — do not load it otherwise, to save context.

Always referenced (never auto-loaded): `docs/INVARIANTS.md`,
`docs/PHASES.md`, `docs/ROADMAP.md`.

## 3. Repository Map

| Repo | Access | Role |
|---|---|---|
| `ApexAgentManagementChat` | **read-write** | All commits land here. |
| `LeanJavallm` | read-only | Reference. Reimplement, do not copy. |
| `Agenticplatformsetup` | read-only | Patterns only. Its Java files do not exist. |

## 4. Non-Negotiable Prohibitions

Violating any of these is a build failure. No exceptions. No "just this
once." No "I'll fix it after."

1. Never use `Runtime.exec(String)` or shell interpolation (`/bin/sh -c`,
   `cmd.exe /c`, string concatenation into a command).
   2. Never use `defineClass`, `URLClassLoader`, or any dynamic bytecode.
   3. Never use `ScopedValue` (preview in Java 21 LTS).
   4. Never put a mutable collection in a record without `List.copyOf` /
      `Map.copyOf` in the compact constructor.
      5. Never write placeholder bodies (`return null`, `TODO`, `throw new
         UnsupportedOperationException`).
         6. Never disable, skip, or `@Ignore` a test to make the build pass.
         7. Never proceed to the next phase without explicit human approval.
         8. Never add Spring Boot, LangChain4j, JDBC, Flyway, or React before the
            phase that requires it (see `docs/PHASES.md`).
            9. Never hand-edit `client/src/generated/`. It is CI-generated.
            10. Never add `any` to TypeScript. Use `unknown` + Zod parse.
            11. Never copy code from `LeanJavallm` or `Agenticplatformsetup`.
                Reimplement against Apex interfaces.
                12. Never assume `Agenticplatformsetup`'s Java files exist. They don't.
                    Patterns only, per `SALVAGE.md`.
                    13. Never bypass the `SandboxValidationSentinel` (P5).
                    14. Never claim a capability, test result, or invariant the code doesn't
                        actually provide. You cannot run `mvn test` — do not say you did.
                        15. Never commit directly to `main`. Use `phase/p<n>-<slug>` branches.

                        ## 5. Invariants — Quick Reference

                        Full detail in `docs/INVARIANTS.md`. IDs only here for cross-referencing.

                        - **K1** No dynamic JVM classloading
                        - **K2** No shell interpolation (`ProcessBuilder(List<String>)` only)
                        - **K3** Kernel invariants are compiled constants
                        - **K4** Canonical workspace root via `toRealPath()` + `startsWith`
                        - **K5** Kill process tree via `ProcessHandle.descendants()`
                        - **K6** Cooperative cancellation via `CancellationToken`, ≤100 ms
                        - **K7** OS injection guarded by dead-man + rate limit + password block
                        - **E1** Sentinel runs on every `writeWorkspaceFile`
                        - **E2** Capability escalation requires signed `PermissionGrant`
                        - **E3** Sentinel failure → atomic rollback
                        - **E4** Commits cannot reference missing blobs
                        - **E5** Branch CAS loses no writes under concurrency
                        - **C1** TS types isomorphic to Java OpenAPI (CI `git diff --exit-code`)
                        - **C2** Zod validation at every TS/Java boundary
                        - **C3** OSC padding `(4 - (len % 4)) % 4`; full type tags `,i ,f ,s ,d`
                        - **C4** `ContextCompactor` preserves SYSTEM + tool-call/result pairs

                        If a required change would violate an invariant, stop and report (§8.3).

                        ## 6. Commit Format
                        
                        ```

                        <type>(<phase>): <subject>

                        <optional body: what and why>

                        Refs: P<n>

                        ```

                        - Types: `feat`, `fix`, `test`, `refactor`, `docs`, `build`, `chore`
                        - Subject: imperative, ≤72 chars
                        - Branch per phase: `phase/p1-kernel-core`, `phase/p2-cas`, ...
                        - Squash-merge to `main` only after CI is green + human approves

                        ## 7. Verification (before every commit)

                        AI Studio runtime cannot compile Java or run Maven. CI is the verifier.

                        1. Read your own diff. If you can't explain a line, rewrite it.
                        2. Confirm your file list matches `docs/PHASES.md §<phase>` — no extras.
                        3. Confirm no prohibited dependency was added (see §4.8).
                        4. Push to the phase branch. CI runs.
                        5. If CI is red, read the log, fix, push again. Do not report "done" on
                           red. Do not say "tests pass" — say "CI status: green/red" and link it.

                           ## 8. Stop-and-Report Triggers

                           Stop and wait for human input when:

                           1. Phase exit criteria are met — report and wait.
                           2. A salvage source doesn't match `SALVAGE.md` — stop, report the diff.
                           3. An invariant would be violated by the requested change.
                           4. A wire-format decision (OpenAPI schema, SSE event shape) is not
                              already specified in `docs/PHASES.md` or `docs/INVARIANTS.md`.
                              5. A dependency not listed in the phase spec is needed.
                              6. You find a bug in prior-phase code — report, do not silently fix.
                              7. CI fails twice on the same root cause — stop, do not thrash.

                              Do NOT stop for: variable names, log levels, test fixture data, import
                              ordering. Make a reasonable choice; note it in the commit body.

                              ## 9. Tone

                              Terse. Factual. No emoji. No preamble. Report what you did, what CI
                              verified, what's blocked. If you made a judgment call, say so and why.
                              ```
                              