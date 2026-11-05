---
bundle: project-charter
profile: 02-instantiation
target_branch: bootstrap/project-charter
file_count: 5
file_index:
  - AGENTS.md
  - 00_context/goal.md
  - README.md
  - docs/PROJECT.md
  - docs/TASKS.md
---

## Agent Instructions

Read this bundle in full. Verify `file_count` matches the fenced blocks.
If they match, create the branch and write the files exactly as shown.
Two files (`AGENTS.md` and `00_context/goal.md`) replace the baseline
versions; three are new. Commit in the order listed below. Push and
report.

## Files

**AGENTS.md**
```markdown
# AGENTS.md — Workspace Router

## Project
Apex: a self-evolving multi-agent platform. Java 21 microkernel
(Spring Boot 3.3, LangChain4j, Virtual Threads) plus a Content-
Addressable Storage evolution plane. Tauri 2.0 + React 19 client.

## Build & Test
- Install: `./mvnw -q dependency:resolve`
- Test: `./mvnw -q test`
- Lint: `./mvnw -q -DskipTests package`
- Client (from P7): `pnpm install && pnpm test`

## Routing Table

| Task | Read this | Skip |
|---|---|---|
| Understand the goal | `00_context/goal.md`, `00_context/CONTEXT.md` | everything else |
| Research a topic | `01_research/CONTEXT.md`, `.agents/skills/research/SKILL.md` | code dirs |
| Design a change | `02_design/CONTEXT.md`, `.agents/skills/design/SKILL.md` | scratch |
| Write code | `03_execute/CONTEXT.md`, `.agents/skills/execute/SKILL.md`, `.agents/rules/file-placement.md` | research archives |
| Review / test | `04_verify/CONTEXT.md`, `.agents/skills/verify/SKILL.md`, `.agents/rules/review-gates.md` | design drafts |
| Look up a term | `reference/_index.md` | — |

## Hard Rules
- Always-loaded rules live in `.agents/rules/`. Read `_index.md` first.
- Never write outside the folder allowed by `file-placement.md`.
- Never advance past a stage without its review gate.
- Scratch work goes in `scratch/`. Approved work goes in `output/`.
- If a file exceeds its line budget, split it and link the parts.

## Context Discipline
- Do not preload files. Read on demand per the routing table.
- Every file you create must respect the budgets in
  `.agents/rules/context-discipline.md`.