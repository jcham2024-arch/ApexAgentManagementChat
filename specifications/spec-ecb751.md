# PHASE-S2-KERNEL.md

**Version:** 1.0 · **Phase:** S2 · **Router only**

## 0. What this file is

S2 is the coding kernel. It is the smallest system that can read a
task file, run a plan, call a model, execute tools, and report. Once
S2 is green, the kernel is self-hosting: S3–S8 become task files.

S2 is split into eight sub-phases. This file names them and the exit
criteria for S2 as a whole.

## 1. Reading protocol

Load in this order:

1. `AGENT_RULES.md`
2. `CONTEXT.md`
3. This file (the router)
4. **The S2 context notes** (pasted by the human)
5. **The sub-phase file named by the human for this session**
6. **Spec sections the sub-phase file requires** (see §3)

If the human names two sub-phase files in one session, stop and report.

## 2. Sub-phases

| Sub-phase | File | Scope | Ends with |
|---|---|---|---|
| S2.0 | `PHASE-S2.0-MODULE-SKELETONS.md` | Module POMs, `package-info`, `docs/INVARIANTS.md` stub, CI narrowing | `mvn test` green |
| S2.1 | `PHASE-S2.1-PORTS.md` | Seven port-contract records + nine port interfaces + `docs/PORT-CATALOGUE.md` | All ports compile with tests |
| S2.2 | `PHASE-S2.2-PLANNER.md` | `AStarPlanner` + `PlanLoop` in `engine-planner` | Determinism, admissibility, risk weighting proven |
| S2.3 | `PHASE-S2.3-TOOLS.md` | Five tools in `engine-tools-impl` | Injection tests pass |
| S2.4 | `PHASE-S2.4-MODEL-ADAPTER.md` | `OpenAiCompatibleProvider` in `engine-llm-http` | Mocked provider streams a response |
| S2.5 | `PHASE-S2.5-STORAGE.md` | SQLite `step_log` in `engine-store-sqlite` | Steps persist and query |
| S2.6 | `PHASE-S2.6-CLI.md` | `TaskRunner` + task schema + CLI entry in `engine-cli` | Task file loads and validates |
| S2.7 | `PHASE-S2.7-E2E.md` | End-to-end kernel test | Kernel completes one task file unsupervised |

## 3. Spec dependency

`docs/apex-chat-spec/` is **not yet committed** to the repo. S2.2 and
S2.4 need spec sections (Parts 3, 6, 8). Before those sub-phases, the
human pastes the relevant sections into the prompt. If a sub-phase
names a spec part and it is not pasted, stop and report which part is
missing.

S2.0 and S2.1 do **not** need the spec. They are self-contained.

## 4. Modules introduced in S2

Eight modules, not all in `CONTEXT.md` §4. Treat these as authoritative
for S2. `CONTEXT.md` §4 gets updated in S2.7.

| Module | Tier | Role |
|---|---|---|
| `apex-core` | parent | Aggregator |
| `apex-core/engine-core` | 0 | Ports + loop |
| `apex-core/engine-planner` | 0 | A* planner |
| `apex-core/engine-tools` | 0 | Tool ports |
| `apex-core/engine-store` | 0 | Storage ports |
| `engine-tools-impl` | 1 | Five tools |
| `engine-llm-http` | 1 | OpenAI-compatible client |
| `engine-store-sqlite` | 1 | SQLite `step_log` |
| `engine-cli` | 1 | Task loader + runner |

## 5. S2 exit criteria

A. All eight sub-phases green in CI.
B. The kernel completes `tasks/bootstrap-verify.yaml` unsupervised.
C. `run_report.md` names: model used, steps taken, cost, exit status.
D. The kernel pushes to `agent/bootstrap-verify` and the branch exists.
E. No file over 200 lines. No enterprise dependency in a Tier 0
   module.

## 6. Do NOT do in S2

- No Spring, Google, Embabel, Jackson, Micrometer.
- No flywheel, security, memory router, server, GUI, council, node.
- No adapter that is not named in §4.

## 7. Commit discipline

One branch per sub-phase: `phase/s2.0-skeletons`, `phase/s2.1-ports`,
etc. Squash-merge each to `main` after human review.

---

**End of router.** Read the sub-phase file the human named.