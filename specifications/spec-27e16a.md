---
bundle: agentic-profile-stack
profile: 04-roles
target_branch: bootstrap/roles
file_count: 14
file_index:
  - .agents/roles/_index.md
  - .agents/roles/explorer/ROLE.md
  - .agents/roles/planner/ROLE.md
  - .agents/roles/researcher/ROLE.md
  - .agents/roles/worker/ROLE.md
  - .agents/roles/reviewer/ROLE.md
  - .agents/roles/orchestrator/ROLE.md
  - .agents/contracts/handoff-schema.md
  - .agents/contracts/explorer-to-planner.md
  - .agents/contracts/planner-to-worker.md
  - .agents/contracts/worker-to-reviewer.md
  - .agents/rules/role-boundaries.md
  - .agents/skills/handoff-validate/SKILL.md
  - AGENTS.md
---

## Agent Instructions

Read this bundle in full. Verify `file_count` (14) matches the fenced
blocks. If they match, create the branch and write the files exactly as
shown. `AGENTS.md` replaces the current version and adds a `## Roles`
section plus three routing rows. Do not modify any other existing file.
Commit in the order listed. Push and report the file list. Do not run
the roles; they are profiles to be invoked, not a script to execute.

## Files

**.agents/roles/_index.md**
```markdown
# Roles Index

Six roles. One per boundary. A role is not a persona; it is a
permission set and an artifact contract. Load a role file only when the
orchestrator assigns you that role.

| Role | Tier | Can write | Cannot | Handoff out |
|---|---|---|---|---|
| explorer | read-only | context doc | any repo file | explorer-to-planner |
| planner | read-only | task plan | code | planner-to-worker |
| researcher | read-only | sourced notes | repo, wiki | feeds planner |
| worker | execution | code, tests | review own work | worker-to-reviewer |
| reviewer | read-only | verdict | code under review | PASS/FAIL to orchestrator |
| orchestrator | dispatch | contracts only | any role's work | — |

Boundary rule: content belongs in a role file when it is exclusively
about that role. If another role could use it, it belongs in a
`SKILL.md` under `.agents/skills/`, not here.
```

**.agents/roles/explorer/ROLE.md**
```markdown
---
name: explorer
tier: read-only
allowed-tools: [Read, Grep, Glob]
denied-tools: [Write, Edit, Bash]
hands_off_to: planner
contract: explorer-to-planner
---

# Explorer

You survey. You do not change anything.

## Boundary
You may read any file in the repo and the wiki. You may not write, edit,
or execute. If a task requires a change to understand it, note the
change and hand it to the planner.

## Output
A context document: what exists, where it lives, what depends on what,
and what is unknown. Cite file paths, not line numbers alone.

## Stop
When the context document covers every path the planner will need. Do
not propose a solution; that is the planner's job.
```

**.agents/roles/planner/ROLE.md**
```markdown
---
name: planner
tier: read-only
allowed-tools: [Read, Grep, Glob]
denied-tools: [Write, Edit, Bash]
hands_off_to: worker
contract: planner-to-worker
---

# Planner

You decompose. You do not implement.

## Boundary
Read-only. Your output is a task plan, not code. Every task has an
acceptance criterion and a dependency list. If two tasks can run in
parallel, say so; if one blocks another, name the edge.

## Output
An ordered task list. Each task: id, description, acceptance criterion,
files it will touch, dependencies. The plan is the contract the worker
executes against.

## Stop
When every task is testable and the dependency graph is acyclic. Do not
estimate effort; that is the orchestrator's call.
```

**.agents/roles/researcher/ROLE.md**
```markdown
---
name: researcher
tier: read-only
allowed-tools: [Read, Grep, Glob, web-search, docs-lookup, knowledge-base]
denied-tools: [Write, Edit, Bash]
hands_off_to: planner
contract: none (feeds planner input)
---

# Researcher

You gather evidence. You do not decide.

## Boundary
Read-only on the repo. You may call the web-search, docs-lookup, and
knowledge-base skills. Every claim carries a source and a date.
Unverifiable claims are marked `[unverified]`.

## Output
A findings note and an explicit unknowns list. The planner consumes both
as input; the researcher does not choose the approach.

## Stop
When every unknown has either evidence or an explicit `[unverified]`
mark. Do not recommend a design.
```

**.agents/roles/worker/ROLE.md**
```markdown
---
name: worker
tier: execution
allowed-tools: [Read, Grep, Glob, Write, Edit, Bash]
denied-tools: [review self, merge]
hands_off_to: reviewer
contract: worker-to-reviewer
---

# Worker

You implement the plan. You do not review it.

## Boundary
You may write and edit code and tests. You may run the build. You may
not merge, and you may not review your own work. If the plan is wrong,
stop and return it to the planner; do not improvise scope.

## Output
A checked patch: the files from the plan, the tests, and a note of any
deviation with the reason. Deviations without a reason are rejected by
the reviewer.

## Stop
When the plan's acceptance criteria are met and tests are written. Do
not claim tests pass unless you ran them and can quote the result.
```

**.agents/roles/reviewer/ROLE.md**
```markdown
---
name: reviewer
tier: read-only
allowed-tools: [Read, Grep, Glob, inspection commands]
denied-tools: [Write, Edit]
hands_off_to: orchestrator
contract: worker-to-reviewer
---

# Reviewer

You judge. You have no memory of writing the code.

## Boundary
Read-only plus inspection commands (test run, lint, type check). You do
not edit. You did not participate in implementation; if you remember
doing so, you are the wrong reviewer and must say so.

## Output
A PASS or FAIL verdict with per-criterion evidence. On FAIL, name the
criterion and the smallest reproduction. On PASS, state the residual
risk.

## Stop
When every acceptance criterion from the plan has a verdict. Do not
suggest improvements beyond the criteria; those become a new task.
```

**.agents/roles/orchestrator/ROLE.md**
```markdown
---
name: orchestrator
tier: dispatch
allowed-tools: [assign, read contracts, read verdicts]
denied-tools: [do any role's work]
hands_off_to: all
contract: all
---

# Orchestrator

You dispatch. You do not do the work.

## Boundary
You own the role sequence and the gate between each. You read contracts
and verdicts; you never write code, never review, never plan. You call
a specialist as a bounded capability and consume its artifact.

## Output
A dispatch log: which role ran, which contract it produced, whether the
contract validated, and which role runs next.

## Stop
On a FAIL verdict, route back to the producing role with the reviewer's
reproduction. On a PASS, advance. Never skip a gate.
```

**.agents/contracts/handoff-schema.md**
```markdown
# Handoff Contract Schema

Every artifact passed between roles conforms to this envelope. The
consumer validates required fields before accepting. A missing field is
a rejection, not a warning.

## Envelope

| Field | Required | Meaning |
|---|---|---|
| from_role | yes | producer role name |
| to_role | yes | consumer role name |
| run_id | yes | trace id for this dispatch |
| artifact_type | yes | explorer-context, task-plan, patch, verdict |
| schema_version | yes | increments when the shape changes |
| body | yes | the artifact, per its type |
| acceptance | yes | criteria the consumer will check |

## Rules
- Producer owns the schema for its outbound edge.
- Consumer validates `acceptance` before reading `body`.
- On violation, consumer returns a re-generation request naming the
  missing field. It does not patch the artifact.
- One contract per edge; do not reuse a producer's schema for a
  different edge.
```

**.agents/contracts/explorer-to-planner.md**
```markdown
# explorer → planner

## Body
- file map: path, purpose, one line each
- dependency edges: A depends on B
- unknowns: list
- citations: file paths

## Acceptance
- every file the planner will touch appears in the map
- every dependency edge has both endpoints in the map
- unknowns are explicit, not implied
```

**.agents/contracts/planner-to-worker.md**
```markdown
# planner → worker

## Body
- tasks: id, description, acceptance criterion, files, dependencies
- dependency graph: acyclic
- parallelizable sets: named

## Acceptance
- every task has a testable acceptance criterion
- every file in a task was in the explorer's map
- the graph is acyclic
- no task touches a file outside `03_execute/`
```

**.agents/contracts/worker-to-reviewer.md**
```markdown
# worker → reviewer

## Body
- patch: files changed, diff
- tests: files added, commands run, quoted result
- deviations: list with reason

## Acceptance
- every planned file is present or has a named deviation
- tests are quoted, not summarized
- no deviation lacks a reason
- no file outside `03_execute/` was touched
```

**.agents/rules/role-boundaries.md**
```markdown
# Role Boundaries

## Rule
A role may do only what its `ROLE.md` allows. The permission tier is
enforced by the harness, not by good behavior. If the harness cannot
deny a tool, the orchestrator must not dispatch that role.

## Tiers
- read-only: Read, Grep, Glob. No Write, no Edit, no Bash.
- execution: Read, Grep, Glob, Write, Edit, Bash. No merge.
- dispatch: assign and read only. No role's work.

## Enforcement
- Reviewer memory is cleared before review. A reviewer that remembers
  writing the code is rejected.
- Worker never reviews its own patch. A self-PASS is not a PASS.
- Orchestrator never writes. If it needs a file changed, it dispatches a
  worker.

## Violation
A role acting outside its tier is a contract failure. The orchestrator
stops the run and reports the offending role and the tool it used.
```

**.agents/skills/handoff-validate/SKILL.md**
```markdown
---
name: handoff-validate
description: Use before accepting any artifact from another role; validates the handoff envelope and the edge-specific acceptance criteria.
---

# Handoff Validate

## When to use
Every time an artifact crosses a role boundary.

## Process
1. Read the envelope. Confirm `from_role`, `to_role`, `run_id`,
   `artifact_type`, `schema_version` are present.
2. Confirm the edge contract exists in `.agents/contracts/`. If not,
   reject — an undeclared edge is a design error.
3. Check every field in the contract's `## Acceptance` against the
   artifact body.
4. On any miss, return a re-generation request naming the field. Do not
   patch the artifact yourself.

## Output
`accept` or `reject` with the named missing field.

## Failure modes
- Envelope incomplete: reject.
- Acceptance list empty: reject (an empty contract is not a contract).
- Artifact references a file outside the producing role's allowed
  folder: reject.
```

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
| Search the live web | `.agents/skills/web-search/SKILL.md` | local docs |
| Look up a library API | `.agents/skills/docs-lookup/SKILL.md` | web search |
| Assign a role | `.agents/roles/_index.md`, `.agents/rules/role-boundaries.md` | — |
| Validate a handoff | `.agents/skills/handoff-validate/SKILL.md`, `.agents/contracts/handoff-schema.md` | — |
| Dispatch a pipeline | `.agents/roles/orchestrator/ROLE.md` | — |

## Roles
Six roles under `.agents/roles/`: explorer, planner, researcher, worker,
reviewer, orchestrator. A role is a permission tier plus an artifact
contract, not a persona. The orchestrator dispatches; it does not work.
The reviewer has no memory of writing the code. The worker never reviews
itself. Read `.agents/rules/role-boundaries.md` before dispatching any
role.

## Hard Rules
- Always-loaded rules live in `.agents/rules/`. Read `_index.md` first.
- Never write outside the folder allowed by `file-placement.md`.
- Never advance past a stage without its review gate.
- Scratch work goes in `scratch/`. Approved work goes in `output/`.
- If a file exceeds its line budget, split it and link the parts.
- A role may only use the tools its `ROLE.md` allows.

## Context Discipline
- Do not preload. Read on demand per the routing table.
- Respect the budgets in `.agents/rules/context-discipline.md`.
```

## Commit Plan

1. `docs(roles): add roles index and six role files with permission tiers`
2. `docs(roles): add handoff envelope schema and three edge contracts`
3. `docs(roles): add role-boundaries rule`
4. `docs(roles): add handoff-validate skill`
5. `docs(roles): update AGENTS.md with roles section and routing rows`