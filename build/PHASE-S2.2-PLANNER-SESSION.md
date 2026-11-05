# PHASE-S2.2-PLANNER-SESSION.md — Session Package, Part 1 of 5

**Version:** 1.0 · **Sub-phase of S2** · **Part 1 of 5:** reading protocol, context deltas, prerequisite, scope.
Parts: SESSION (§0–§3) · PRIMITIVES (§4.1a) · PLANNER (§4.1b) · LOOP
(§4.2) · TESTS-DOCS (§4.3–§7), each `PHASE-S2.2-PLANNER-<part>.md`. Cut
for the 200-line rule in `AGENT_RULES.md` §2.13.

---
## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S2-KERNEL.md` (repo — router)
4. **This file**

Do not begin until all four are read.

## 1. Context notes — deltas for S2.2

These are authoritative. Where they conflict with `CONTEXT.md` or
`PHASE-S2-KERNEL.md`, they win. Do not stop over them.

### 1.1 The spec dependency is superseded

`PHASE-S2-KERNEL.md` §4 says S2.2 needs spec Parts 3, 6, 8 pasted by the
human. **That requirement is superseded by this file.** The planner is
designed to be self-contained: it defines its own abstract `Action`,
`Condition`, `Goal`, `Plan`, `Blackboard` primitives that do **not**
reference Layer 1 spec types. Bridging to Layer 1 (`AgentStep`,
`AgentDecision`, etc.) happens in S2.7 E2E, not here.

This keeps the planner pure, reusable, and testable without the entire
spec on disk. Do not read spec parts. Do not create spec files.

### 1.2 Two modules get new code

- `engine-planner` (Tier 0) — pure A*, GOAP primitives, no dependencies
  beyond JDK and JSpecify.
- `engine-core` (Tier 0) — `CancellationToken`, `PlanLoop`, and the
  adapter that bridges `AStarPlanner` to `PlannerPort`.

`engine-core` gains a Maven dependency on `engine-planner`.

### 1.3 `CancellationToken` is created here

`CancellationToken` does not exist yet. It is required by `PlanLoop`
and by K6 (cooperative cancellation ≤100ms). Create it in `engine-core`
at package `com.apex.engine.core.concurrent`.

### 1.4 The loop is in `engine-core`, the algorithm in `engine-planner`

`AStarPlanner` (the algorithm) is in `engine-planner`. `PlanLoop` (the
OODA loop that calls the planner repeatedly) is in `engine-core`.
`AStarPlannerPortAdapter` bridges the two so `PlanLoop` depends only on
the `PlannerPort` interface.

### 1.5 No I/O, no Spring, no Jackson

Everything in S2.2 is Tier 0. Only `java.base` and `org.jspecify`.

### 1.6 Determinism is a hard requirement

The `AStarPlanner` must produce identical plans for identical inputs.
This means the `PriorityQueue` comparator must be a total order —
`fScore` alone is insufficient because ties are non-deterministic.
Tie-break on insertion sequence (a monotonic counter).

### 1.7 K3 test is deferred

Invariant K3 ("Kernel invariants are compiled constants") is a Layer 1
concept not yet in the tree. The `docs/INVARIANTS.md` row for K3 says
"S2.2" — treat that as "S2.2 revisits and defers to S3" by updating the
row. Do not implement K3 in S2.2. Do not test it here.

## 2. Prerequisite

S2.1 merged to `main`. Nine ports exist. `mvn -B test` green. If any
is false, stop and report.

## 3. Scope

Deliver:

1. Eleven new types in `engine-planner` (ten sources + updated
   `package-info`)
2. Five new types in `engine-core` (`CancellationToken`, `PlanStatus`,
   `StepRecord`, `PlanLoop`, `AStarPlannerPortAdapter`)
3. `engine-core/pom.xml` gains `engine-planner` dependency
4. Three test classes: `ImmutableBlackboardTest`, `AStarPlannerTest`,
   `PlanLoopTest`
5. `docs/INVARIANTS.md` K3 row updated to "S3"

## 4. Files

All paths relative to repo root.
