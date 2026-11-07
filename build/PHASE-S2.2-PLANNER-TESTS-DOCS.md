# PHASE-S2.2-PLANNER-TESTS-DOCS.md — Session Package, Part 5 of 5

**Version:** 1.0 · **Sub-phase of S2** · **Part 5 of 5:** §4.3–§4.6, §5–§7: POM, tests, invariants, exit criteria, commits.
Parts: SESSION (§0–§3) · PRIMITIVES (§4.1a) · PLANNER (§4.1b) · LOOP
(§4.2) · TESTS-DOCS (§4.3–§7), each `PHASE-S2.2-PLANNER-<part>.md`. Cut
for the 200-line rule in `AGENT_RULES.md` §2.13.

---
### 4.3 `engine-core/pom.xml` — add `engine-planner` dependency

Insert inside `<dependencies>`:

```xml
<dependency>
    <groupId>com.apex</groupId>
    <artifactId>engine-planner</artifactId>
    <version>${project.version}</version>
</dependency>
```

Do not add any other dependency. If the Enforcer runs and complains
about anything, remove it — do not disable the Enforcer.

### 4.4 Tests

**`engine-planner/src/test/java/com/apex/engine/planner/ImmutableBlackboardTest.java`**

Cover:
- `empty()` has empty snapshot
- `getString` returns empty for missing key
- `getString` returns value for present key
- `get(key, WrongClass)` returns empty
- `with` returns a new instance; original is unchanged
- Snapshot is unmodifiable (`UnsupportedOperationException` on put)

**`engine-planner/src/test/java/com/apex/engine/planner/AStarPlannerTest.java`**

Test cases:
1. **Determinism** — call `plan(...)` twice on the same inputs; assert
   identical plans (same step IDs in same order, same cost, same depth).
2. **Goal already satisfied** — empty plan returned with cost 0.
3. **Single action path** — one action with no preconditions,
   effects satisfy goal → plan has one step.
4. **Two action path** — action A sets key1, action B needs key1 and
   sets key2 (goal) → plan has both actions in order.
5. **No plan possible** — action effects never satisfy goal → empty
   Optional.
6. **Risk weighting** — two paths to the same goal with equal base
   cost; one uses L4 action, one uses L1. The planner picks the L1
   path.
7. **Admissibility** — for a linear chain of N actions, the returned
   plan cost equals the sum of base costs plus penalties.
8. **Depth limit** — a goal requires 5 actions but `maxDepth=3` → empty
   Optional.

Helper: a `StubAction` inner class or record implementing `Action` with
parameterized preconditions, effects, cost, risk, and simple execute
that returns `ActionOutput.Success(applyEffects(state))`.

**`engine-core/src/test/java/com/apex/engine/core/loop/PlanLoopTest.java`**

Test cases:
1. **Completes on satisfied goal** — one no-precondition action; loop
   returns `COMPLETED` after 1 step.
2. **STUCK when no plan possible** — goal unsatisfiable; loop returns
   `STUCK` with 0 steps.
3. **FAILED on action failure** — one action whose execute returns
   `Failure`; loop returns `FAILED` after 1 step.
4. **MAX_STEPS_EXCEEDED** — action that never satisfies the goal;
   `maxSteps=3` → returns `MAX_STEPS_EXCEEDED` with 3 steps.
5. **Cancellation (K6)** — one action that sleeps 500ms in
   `execute`; `CancellationToken` cancelled after 50ms; assert loop
   returns `CANCELLED` within 100ms.
6. **Replans each step** — a `CountingAction` that increments a
   counter per execution; assert planner is invoked once per step (via
   a spy or a `StubPlanner` wrapper — or assert via step count).

### 4.5 Update `docs/INVARIANTS.md`

In the K3 row, change the Test column from "S2.2" to "S3 (deferred —
Layer 1 concept not yet in tree)".

## 5. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `AStarPlannerTest` passes all 8 test cases.
- [ ] `PlanLoopTest` passes all 6 test cases, including K6 cancellation
      within 100ms.
- [ ] `ImmutableBlackboardTest` passes all 6 test cases.
- [ ] `engine-core/pom.xml` depends on `engine-planner`.
- [ ] `docs/INVARIANTS.md` K3 row updated to "S3".
- [ ] All ArchUnit Tier 0 rules still pass on both modules.
- [ ] No file exceeds 200 lines.
- [ ] No enterprise dependency anywhere.
- [ ] No spec files created. No Layer 1 types referenced.

## 6. Do NOT add

- Any tool implementation (S2.3)
- Any LLM client (S2.4)
- Any storage adapter (S2.5)
- Any CLI (S2.6)
- Any Layer 1 type reference (`AgentStep`, `AgentDecision`, etc. — those
  are bridged in S2.7)
- Any port implementation beyond `AStarPlannerPortAdapter` if you find
  it necessary for the tests (see §4.6 — it is optional in S2.2)
- Any dependency beyond JSpecify, JUnit 5, AssertJ
- Any spec file
- Any file not named in §4

### 4.6 On `AStarPlannerPortAdapter`

The router §4.2 mentioned this file, but the actual tests in §4.4 do
not require it — `PlanLoop` uses `AStarPlanner` directly in S2.2. The
adapter that implements `PlannerPort` (from S2.1) is deferred to S2.6
(CLI) or S2.7 (E2E), whichever wires it first. Do not create it in
S2.2 unless a test genuinely requires it. If it is not created, remove
it from §3 scope.

## 7. Commit

Three commits on `phase/s2.2-planner`:

1. `feat(S2.2): add GOAP primitives and A* planner in engine-planner`
2. `feat(S2.2): add CancellationToken, PlanLoop, and loop result in engine-core`
3. `docs(S2.2): defer K3 to S3 in INVARIANTS.md`

Push. Report CI link, test counts per class, and confirmation that K6
(cancellation ≤100ms) is proven by a test. If CI fails twice on the same
root cause, stop and report the log.

---

**End of S2.2 session package.**
