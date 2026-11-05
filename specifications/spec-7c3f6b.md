---
bundle: bootstrap-genesis
profile: 00-genesis
target_branch: bootstrap/genesis
file_count: 1
file_index:
  - BOOTSTRAP.md
---

## Agent Instructions

1. Create the branch `bootstrap/genesis` from `main`.
2. Write the file `BOOTSTRAP.md` at the repo root with the exact
   content shown below.
3. Read `BOOTSTRAP.md` in full and execute its instructions. It will
   tell you to create the baseline workspace files, commit them, and
   then delete `BOOTSTRAP.md` and commit the deletion.
4. Do not skip the deletion step. `BOOTSTRAP.md` is a one-time genesis
   file; it must not remain in the final tree.

## Files

**BOOTSTRAP.md**
```markdown
# BOOTSTRAP.md — Genesis Agent

You are the genesis agent. Your only job is to create the baseline
agentic workspace described below, commit it, and then delete this file.
You are not the final agent. The file you are reading is temporary.

## What to build

A four-stage workspace with a small always-loaded router (`AGENTS.md`),
scoped rules (`.agents/rules/`), stage skills (`.agents/skills/`), and
numbered stage folders (`00_context` through `04_verify`). File budgets
are hard limits: the router is 15–40 lines, rule files are 10–30 lines,
and no file exceeds 300 lines. If a file would exceed its budget, split
it and link the parts.

## Step 1 — Create `AGENTS.md`

This is the final router. Keep it under 40 lines. Use this exact
structure:

```markdown
# AGENTS.md — Workspace Router

## Project
<One-line description of this project.>

## Build & Test
- Install: `<command>`
- Test: `<command>`
- Lint: `<command>`

## Routing Table

| Task | Read this | Skip |
|---|---|---|
| Understand the goal | `00_context/goal.md`, `00_context/CONTEXT.md` | everything else |
| Research | `01_research/CONTEXT.md`, `.agents/skills/research/SKILL.md` | code dirs |
| Design | `02_design/CONTEXT.md`, `.agents/skills/design/SKILL.md` | scratch |
| Write code | `03_execute/CONTEXT.md`, `.agents/skills/execute/SKILL.md`, `.agents/rules/file-placement.md` | research archives |
| Review | `04_verify/CONTEXT.md`, `.agents/skills/verify/SKILL.md`, `.agents/rules/review-gates.md` | design drafts |
| Look up a term | `reference/_index.md` | — |

## Hard Rules
- Read `.agents/rules/_index.md` first.
- Never write outside the folder allowed by `file-placement.md`.
- Never advance past a stage without its review gate.
- Scratch work goes in `scratch/`. Approved work goes in `output/`.
- If a file exceeds its line budget, split it.

## Context Discipline
- Do not preload. Read on demand per the routing table.
- Respect the budgets in `.agents/rules/context-discipline.md`.
```

## Step 2 — Create `CLAUDE.md`

One line only: `See AGENTS.md. AGENTS.md is canonical.`

## Step 3 — Create `.agents/rules/_index.md`

A table of the three rule files and when to load them. Keep it under 20
lines.

## Step 4 — Create the three rule files

**`.agents/rules/context-discipline.md`** — 10–30 lines. Cover: router
budget 15–40 lines, rule files 10–30 lines, domain guides under 300
lines, `SKILL.md` under 500 lines. Principle: read just-in-time, push
state to `scratch/`, split files rather than compressing prose.

**`.agents/rules/file-placement.md`** — a table mapping folders to
what they hold and their mutability. `00_context` is read-only after
gate 0; `03_execute` is the only place code lives; `scratch` is never
cited in output.

**`.agents/rules/review-gates.md`** — four gates, one per stage. Gate 1:
every claim sourced or marked `[unverified]`. Gate 2: one approach
chosen, blast radius stated. Gate 3: code compiles, tests written, no
placeholders. Gate 4: tests and lint pass, human signs off, artifact
moves to `output/`.

## Step 5 — Create four skills under `.agents/skills/`

Each skill is a directory with a `SKILL.md` file. `SKILL.md` starts with
YAML front-matter (`name`, `description`) followed by `## Inputs`,
`## Process`, `## Outputs`, and `## Gate`. Keep each under 40 lines.

- `research/SKILL.md` — separate known from unknown, gather evidence,
  cite sources, mark unverifiable claims `[unverified]`.
- `design/SKILL.md` — write at least two approaches, compare cost, risk,
  reversibility, blast radius; choose one and record why the others
  lost.
- `execute/SKILL.md` — create only the files listed in the approved
  proposal; follow `file-placement.md`; write tests first if the design
  specifies one.
- `verify/SKILL.md` — run the test and lint commands from `AGENTS.md`,
  check for placeholders and scope creep, write results with log
  excerpts.

## Step 6 — Create the five stage folders

Each stage folder gets a `CONTEXT.md` with `## Inputs`, `## Process`,
`## Outputs`, and `## Review Gate`. Keep each under 30 lines. The
content is stage-specific:

- `00_context/CONTEXT.md` — restate the goal, list scope and
  non-negotiables, list unknowns. Gate 0: human confirms the goal.
- `01_research/CONTEXT.md` — separate known from unknown, gather
  evidence, cite sources. Gate 1: every claim sourced or marked
  unverified.
- `02_design/CONTEXT.md` — write two approaches, compare, choose one,
  list files. Gate 2: one approach chosen, blast radius stated.
- `03_execute/CONTEXT.md` — create only files in the proposal, write
  tests. Gate 3: compiles, tests written, no placeholders.
- `04_verify/CONTEXT.md` — run tests and lint, check for scope creep.
  Gate 4: all pass, human signs off, artifact moves to `output/`.

Also create `00_context/goal.md` as a template with `## In Scope`,
`## Out of Scope`, and `## Non-Negotiables` sections, and placeholders
for the project-specific content.

## Step 7 — Create the reference index and empty folders

- `reference/_index.md` — a table with columns `Topic`, `File`, and
  `One-line description`. Keep it under 30 lines.
- `scratch/.gitkeep`, `review/.gitkeep`, `output/.gitkeep` — empty
  placeholder files.
- `.gitignore` — ignore `scratch/*` but keep `.gitkeep`, and ignore
  OS and IDE files.

## Step 8 — Commit

Commit in this order:

1. `chore(genesis): add router, CLAUDE.md pointer, gitignore`
2. `docs(genesis): add rules index and three rule files`
3. `docs(genesis): add four stage skills with SKILL.md frontmatter`
4. `docs(genesis): add stage contexts, goal template, reference index, empty folders`

## Step 9 — Delete this file

After all files are committed, delete `BOOTSTRAP.md` and commit:

`chore(genesis): remove one-time bootstrap file`

Push the branch. Report the final file list and confirm that
`BOOTSTRAP.md` is no longer present in the tree.
```

## Commit Plan

1. `chore(genesis): add one-time BOOTSTRAP.md genesis file`
2. The agent executes the bootstrap content, which produces four
   additional commits (Steps 1–8) and a final deletion commit
   (Step 9).