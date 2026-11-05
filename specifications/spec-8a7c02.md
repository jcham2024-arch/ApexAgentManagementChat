---
bundle: stacks-index
profile: 08-index
target_branch: bootstrap/stacks-index
file_count: 1
---

## Files

**STACKS.md**
```markdown
# STACKS.md — Installed Stack Index

Six layers. One pick per layer. Do not add a second tool to a layer that
already has one.

| # | Stack | Layer | Status | File |
|---|---|---|---|---|
| 00 | Bootstrap | Workspace structure | installed | `BOOTSTRAP.md` (deleted after genesis) |
| 01 | Resources | Tools, memory | installed | `RESOURCES.md` |
| 02 | Roles | Orchestration, dispatch | installed | `.agents/roles/_index.md` |
| 03 | Evaluation | Trajectory regression | installed | `EVAL.md` |
| 04 | Observability | Tracing, prompt versions | installed | `OBSERVE.md` |
| 05 | Governance | Hooks, budgets, stop | installed | `GOVERN.md` |

## Load order
Read the router (`AGENTS.md`) first. Then load the stack that matches
your task:
- Need to act? → `RESOURCES.md`, `.agents/roles/`.
- Need to judge? → `EVAL.md`.
- Need to debug? → `OBSERVE.md`.
- Need to block? → `GOVERN.md`.

## Install order (once)
1. Bootstrap (genesis).
2. Resources (`scripts/install-resources.sh`).
3. Roles (files only).
4. Evaluation (`scripts/eval-gate.sh` in CI).
5. Observability (`scripts/observe-up.sh`).
6. Governance (hooks + `.spend-guard.json`).

## The rule
A stack is installed only when its health check passes. A missing layer
is safer than a broken one.
```

## Commit Plan

1. `docs(stacks): add master index with six-layer map`