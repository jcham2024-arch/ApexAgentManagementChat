# PHASE-S2.7-E2E-SESSION.md — Session Package, Part 1 of 6

**Version:** 1.0 · **Part 1 of 6:** reading protocol, context deltas, prerequisite, scope.
Parts: SESSION · DECISION-PROMPT · ASSERTIONS · RUNNER · MAIN ·
TESTS-CRITERIA, each `PHASE-S2.7-E2E-<part>.md` (`AGENT_RULES.md` §2.13).

---
## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S2-KERNEL.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S2.7

Authoritative where they conflict with any prior file. Do not stop over
them.

### 1.1 Spec not required

Router §4 says S2.7 needs the full spec pasted. **Superseded.** The
kernel built in S2.0–S2.6 uses its own types (`TaskDefinition`,
`ChatRequest`, `ToolCall`, `StepLogEntry`, etc.) which are all defined
in S2.1–S2.6. Layer 1 types (spec Parts 3, 6, 8) are bridged in S3
when the agent semantic layer is built on top of the kernel. **Do not
read spec parts. Do not create spec files.** If you feel you need them,
stop and report.

### 1.2 The planner is NOT used in S2.7

`AStarPlanner` (S2.2) is tested standalone. The E2E uses a **ReAct
loop** — the LLM decides the next action on each step. A* is wired
into the agent semantic layer in S3. Do not force A* into this phase.
Do not import `AStarPlanner` in the CLI.

### 1.3 Mock LLM server, real everything else

The E2E test uses `com.sun.net.httpserver.HttpServer` to mock the
provider (same pattern as S2.4 tests). Everything else is real: real
SQLite, real tools, real filesystem jail, real `RealTaskRunner`, real
`Main.run`.

### 1.4 `Main.run` gains a `Map<String, String> env` parameter

To let tests inject a mock provider URL without touching
`System.getenv()`, `Main.run` takes an `env` map. `Main.main` passes
`System.getenv()`. Tests pass a custom map. This is the only API change
to `Main`.

### 1.5 `--real` flag; default is stub

`Main` accepts `--real` to activate `RealTaskRunner`. Without it, the
stub runner runs. The S2.6 tests (`validTaskReturns0AndPrintsJson`,
etc.) still pass because they invoke `Main` without `--real`.

### 1.6 `--workspace` flag; default is current directory

`Main` accepts `--workspace <path>`. The workspace is the `RealPathJail`
root, the SQLite location (`.apex/steps.db` under it), and the base for
tool paths. Without the flag, defaults to `Path.of(".").toAbsolutePath()`.

### 1.7 The command registry for E2E is small

`RealTaskRunner` needs a `RunCommandTool` with an allowlist. `Main`
builds a minimal registry: `echo`, `cat`, `bash`. That is enough for
the E2E task and for most coding tasks. Future phases extend it via
config (S3+).

### 1.8 Assertion format is minimal

S2.7 supports two assertion prefixes:
- `file_exists:<path>` — the path must exist as a regular file
- `file_contains:<path>:<substring>` — the file must contain the
  substring

Any other assertion prefix is a **warning**, not a failure: the runner
logs it and continues. S3 hardens this into a proper assertion
interpreter.

### 1.9 `TaskDefinition.modelRoute` empty string triggers `STUB` mode at CLI level

If `model_route` is empty AND `--real` is passed, `Main` exits with
code 1 and stderr "real mode requires model_route". The stub mode
ignores `model_route`.

## 2. Prerequisite

S2.6 merged to `main`. `mvn -B test` green on `main`. If not, stop and
report.

## 3. Scope

Deliver:

1. One new source file: `Decision.java`
2. One new source file: `LlmResponseParser.java`
3. One new source file: `PromptBuilder.java`
4. One new source file: `RealTaskRunner.java`
5. One new source file: `AssertionChecker.java`
6. **Update** `Main.java` to add `--real`, `--workspace`, and `env`
   parameter
7. Five test classes, including the E2E integration test
8. Updated `engine-cli/README.md`
9. **No** `docs/INVARIANTS.md` changes (S2.7 proves the kernel works
   end to end; no single invariant is named)
