# PHASE-S2-KERNEL.md

**Version:** 1.0 · **Phase:** S2 · **Router only**

## 0. What this file is

S2 is the coding kernel. It builds the smallest system that can read a
task file, run a plan, call a model, execute tools, and report. Once S2
is green, the kernel is self-hosting: S3–S8 become task files.

S2 is split into eight sub-phases. This file names them. The human
names exactly one sub-phase file per session.

## 1. Reading protocol

Load in this order:

1. `AGENT_RULES.md`
2. `CONTEXT.md`
3. This file (the router)
4. **The sub-phase file the human attaches this session**
5. **Any spec sections the sub-phase file requires** (see §4)

If the human attaches two sub-phase files in one session, stop and
report. If a required spec section is missing, stop and report which.

## 2. Sub-phases

| Sub-phase | File | Scope | Ends with |
|---|---|---|---|
| S2.0 | `PHASE-S2.0-MODULE-SKELETONS.md` | Module POMs, package-info, INVARIANTS stub, CI narrowing | `mvn test` green |
| S2.1 | `PHASE-S2.1-PORTS.md` | Port interfaces + records + PORT-CATALOGUE | All ports compile with tests |
| S2.2 | `PHASE-S2.2-PLANNER.md` | A* planner + PlanLoop | Determinism, admissibility, risk weighting proven |
| S2.3 | `PHASE-S2.3-TOOLS.md` | Five tools | Injection tests pass |
| S2.4 | `PHASE-S2.4-MODEL-ADAPTER.md` | OpenAI-compatible provider | Mocked provider streams a response |
| S2.5 | `PHASE-S2.5-STORAGE.md` | SQLite step_log | Steps persist and query |
| S2.6 | `PHASE-S2.6-CLI.md` | TaskRunner + task schema | Task file loads and validates |
| S2.7 | `PHASE-S2.7-E2E.md` | End-to-end kernel test | Kernel completes one task unsupervised |

## 3. Modules introduced in S2

Authoritative for S2. `CONTEXT.md` §4 is updated in S2.7.

| Module | Tier | Role |
|---|---|---|
| `apex-core` | parent | Aggregator |
| `apex-core/engine-core` | 0 | Ports + loop |
| `apex-core/engine-planner` | 0 | A* planner |
| `apex-core/engine-tools` | 0 | Tool ports |
| `apex-core/engine-store` | 0 | Storage ports |
| `engine-tools-impl` | 1 | Five tools |
| `engine-llm-http` | 1 | OpenAI-compatible client |
| `engine-store-sqlite` | 1 | SQLite step_log |
| `engine-cli` | 1 | Task loader + runner |

## 4. Spec dependency

`docs/apex-chat-spec/` is **not yet committed**. The dependency per
sub-phase:

| Sub-phase | Needs spec? |
|---|---|
| S2.0 | **No.** Self-contained. |
| S2.1 | **No.** Port interfaces are defined by the S2.1 file. |
| S2.2 | **Yes** — spec Parts 3, 6, 8 pasted by the human. |
| S2.3 | No. |
| S2.4 | **Yes** — spec Part 3 pasted by the human. |
| S2.5 | No. |
| S2.6 | No. |
| S2.7 | **Yes** — full spec pasted by the human. |

If a sub-phase file says "read spec part X" and X is not pasted, stop
and report. Do not guess. Do not create the spec files yourself.

## 5. S2 exit criteria

A. All eight sub-phases green in CI.
B. The kernel completes `tasks/bootstrap-verify.yaml` unsupervised.
C. `run_report.md` names model used, steps taken, cost, exit status.
D. The kernel pushes to `agent/bootstrap-verify` and the branch exists.
E. No file over 200 lines. No enterprise dependency in Tier 0.

## 6. Do NOT do in S2

- No Spring, Google, Embabel, Jackson, Micrometer.
- No flywheel, security, memory router, server, GUI, council, node.
- No adapter not named in §3.
- Do not create `docs/apex-chat-spec/` files yourself. They are
  authoritative when pasted, not invented.

## 7. Commit discipline

One branch per sub-phase: `phase/s2.0-skeletons`,
`phase/s2.1-ports`, etc. Squash-merge after human review.

---

**End of router.** Read the sub-phase file the human attached.
