# PHASE-S2.6-CLI-TESTS-CRITERIA.md — Session Package, Part 6 of 6

**Version:** 1.0 · **Sub-phase of S2** · **Part 6 of 6:** §5–§8, tests, exit criteria, commits.
Parts: SESSION · TASK-TYPES · FIELDS · RUNNER-MAIN · METADATA ·
TESTS-CRITERIA, each `PHASE-S2.6-CLI-<part>.md` (`AGENT_RULES.md` §2.13).

---
## 5. Tests

Package `com.apex.engine.cli` (mirror main).

### 5.1 `TaskLoaderTest.java`

Use `@TempDir Path tempDir`. Helper method `writeTask(String yaml)`
writes to `tempDir.resolve("task.yaml")` and returns the `Path`.

Tests:

1. **loadsValidTask** — full YAML with all fields; assert all record
   fields match.
2. **multiLineInstructionsPreserved** — YAML uses `instructions: |` block;
   assert the loaded string contains the newlines.
3. **optionalFieldsDefaultToEmpty** — minimal YAML (only required
   fields); assert `readFirst`, `outputFiles`, `assertions` are empty,
   `modelRoute` is `""`.
4. **missingTaskIdFails** — assert `TaskLoadException("schema")` with
   message containing `task_id`.
5. **missingDescriptionFails** — same for `description`.
6. **missingInstructionsFails** — same for `instructions`.
7. **missingMaxStepsFails** — same for `max_steps`.
8. **missingToolsFails** — same for `tools`.
9. **emptyToolsListFails** — `tools: []` → throws schema exception.
10. **zeroMaxStepsFails** — `max_steps: 0` → throws schema exception.
11. **nonExistentFileFails** — path to missing file → throws
    `TaskLoadException("io")`.
12. **nonMappingRootFails** — YAML is a scalar (`"hello"`) → throws
    `TaskLoadException("schema")`.
13. **malformedYamlFails** — `"a: [b"` → throws
    `TaskLoadException("yaml")`.
14. **nestedListsParsed** — YAML with `inputs.read_first` and
    `outputs.files` and `outputs.assertions`; assert all three lists
    have the expected contents.

### 5.2 `TaskRunnerTest.java`

1. **stubReturnsFixedResult** — `new StubTaskRunner().run(task)`;
   assert `taskId` matches, `status` is `"STUB"`, `steps` is 0,
   `durationMs` is 0.

### 5.3 `MainTest.java`

Use `ByteArrayOutputStream` for out/err; wrap in `PrintStream`.

1. **noArgsReturnsUsageError** — `run(new String[0], out, err)` → 2;
   stderr contains "usage".
2. **extraArgsReturnsUsageError** — `run(new String[]{"a", "b"}, …)`
   → 2.
3. **missingFileReturns1** — `run(new String[]{"does-not-exist.yaml"},
   …)` → 1; stderr contains "load failed [io]".
4. **validTaskReturns0AndPrintsJson** — write a valid task, run, assert
   exit 0, stdout is a single line, contains `"status":"STUB"`.
5. **invalidTaskReturns1** — write a YAML missing `task_id`, run,
   assert exit 1, stderr mentions `task_id`.

All test classes ≤ 200 lines. Split if exceeded.

## 6. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `TaskLoaderTest` passes all 14 tests.
- [ ] `TaskRunnerTest` passes.
- [ ] `MainTest` passes all 5 tests.
- [ ] SnakeYAML declared in `engine-cli/pom.xml`; no other dependency
      added.
- [ ] `Main.run` returns 0/1/2 as specified; `main` calls
      `System.exit(run(...))`.
- [ ] `TaskLoader` uses `SafeConstructor`; no `new Yaml()` bare
      constructor.
- [ ] `TaskFields` extracted to keep `TaskLoader` under 200 lines.
- [ ] No file exceeds 200 lines.
- [ ] Enforcer passes; `jdeps` gate (Tier 0 only) unaffected.
- [ ] `docs/INVARIANTS.md` unchanged.
- [ ] No other module modified.

## 7. Do NOT add

- Any Spring, Google, Embabel, Jackson, Micrometer dependency
- Any change to `engine-tools-impl`, `engine-llm-http`,
  `engine-store-sqlite`, `engine-planner`, or `engine-core`
- Any use of the ports — the stub runner is intentional
- Any YAML output (only JSON output from `Main`)
- Any subcommands (only `Main <task-file>`)
- Any logging library
- Any test that requires a live network, database, or model
- Any file not listed in §4 or §5

## 8. Commit

Three commits on `phase/s2.6-cli`:

1. `build(S2.6): add snakeyaml dependency to engine-cli`
2. `feat(S2.6): add TaskDefinition, TaskLoader, TaskRunner, and Main CLI`
3. `test(S2.6): add loader, runner, and CLI tests`

Push. Report CI link, per-test-class pass counts, and confirm the
`multiLineInstructionsPreserved` and `validTaskReturns0AndPrintsJson`
tests passed. If CI fails twice on the same root cause, stop and report
the log.

---

**End of S2.6 session package.**
