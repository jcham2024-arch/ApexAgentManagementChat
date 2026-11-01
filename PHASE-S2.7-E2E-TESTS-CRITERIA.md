# PHASE-S2.7-E2E-TESTS-CRITERIA.md — Session Package, Part 6 of 6

**Version:** 1.0 · **Part 6 of 6:** §5–§8, tests, exit criteria, commits.
Parts: SESSION · DECISION-PROMPT · ASSERTIONS · RUNNER · MAIN ·
TESTS-CRITERIA, each `PHASE-S2.7-E2E-<part>.md` (`AGENT_RULES.md` §2.13).

---
## 5. Tests

### 5.1 `DecisionTest.java`

| # | Test |
|---|---|
| 1 | `Action` requires non-blank toolName |
| 2 | `Action` requires non-null argumentsJson |
| 3 | `Done` requires non-null summary |
| 4 | `Action` equality is value-based |

### 5.2 `LlmResponseParserTest.java`

| # | Test |
|---|---|
| 1 | `{"action":"read_file","arguments":{"path":"a"}}` → `Action("read_file", …)` |
| 2 | `{"done":true,"summary":"ok"}` → `Done("ok")` |
| 3 | `{"done":false}` (no action) → throws |
| 4 | `not json` → throws |
| 5 | `{"action":"x"}` (no arguments) → `Action("x","{}")` |
| 6 | Text before JSON (`"Sure. {\"done\":true,\"summary\":\"ok\"}"`) → parses |
| 7 | JSON in code fence (`"\`\`\`json\n{...}\n\`\`\`"`) → parses |
| 8 | Unbalanced braces → throws |
| 9 | String containing `}` inside (e.g. `"arguments":{"path":"a}b"}`) → parses correctly |
| 10 | `arguments` contains nested object | serializes correctly |

### 5.3 `PromptBuilderTest.java`

| # | Test |
|---|---|
| 1 | `initialParts` includes all task tool names |
| 2 | `initialParts` includes task instructions verbatim |
| 3 | `withDecision` appends an `ASSISTANT:` line |
| 4 | `withToolResult` appends a `TOOL_RESULT:` line |
| 5 | History grows by one on each call; original unchanged |

### 5.4 `AssertionCheckerTest.java`

Use `@TempDir Path workspace`.

| # | Test |
|---|---|
| 1 | `file_exists:<path>` passes when file exists |
| 2 | `file_exists:<path>` fails when file missing |
| 3 | `file_contains:<path>:needle` passes when present |
| 4 | `file_contains:<path>:needle` fails when absent |
| 5 | Unknown prefix → `Warn`, not `Fail` |
| 6 | `allPassed` true when all `Pass`/`Warn`, false if any `Fail` |
| 7 | `outputFiles` list is checked even without assertions |

### 5.5 `RealTaskRunnerTest.java`

Uses a **scripted** fake `ChatModelPort` (returns predefined responses
in order) and a real `KernelToolExecutor` + real `SqliteStoragePort` in
`@TempDir`.

Fake model implementation:
```java
final class ScriptedModel implements ChatModelPort {
    private final List<String> responses;
    private int next = 0;
    ScriptedModel(List<String> responses) { this.responses = responses; }
    public ChatResponse chat(ChatRequest req) {
        String r = responses.get(Math.min(next++, responses.size() - 1));
        return new ChatResponse(r);
    }
}
```

| # | Test |
|---|---|
| 1 | Two-step task: `read_file` then `done` → `COMPLETED`, 2 steps |
| 2 | Task with `write_file` then `done` → file exists, assertions pass |
| 3 | `done` on first step → `COMPLETED`, 1 step |
| 4 | Model returns bad JSON → `PARSE_ERROR` |
| 5 | Model always returns same action, `max_steps` hit → `MAX_STEPS` |
| 6 | Assertion fails (file missing) → `ASSERTION_FAILED` |
| 7 | Tool returns error → loop continues (next model response) |
| 8 | Steps are recorded to SQLite (query `stepsFor(sessionId)` returns expected count) — use the session id from the first `StepLogEntry` |

### 5.6 `E2ETest.java` — the milestone test

Full end-to-end: `com.sun.net.httpserver.HttpServer` mocking the
provider + real adapters + `Main.run` invocation.

Setup:
```java
@TempDir Path workspace;

@BeforeEach
void startMockProvider() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
        String body = new String(exchange.getRequestBody().readAllBytes());
        String response = scriptedResponse(body, stepCounter.incrementAndGet());
        byte[] bytes = response.getBytes();
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    });
    server.start();
}
```

`scriptedResponse` returns one of three LLM responses based on step
number:
- Step 1: `{"action":"read_file","arguments":{"path":"ARCHITECTURE.md"}}`
- Step 2: `{"action":"write_file","arguments":{"path":"REPORT.md","content":"Apex is a multi-agent platform."}}`
- Step 3: `{"done":true,"summary":"Wrote REPORT.md"}`

Test body:
1. Write `ARCHITECTURE.md` into `workspace` with content "Apex is a
   multi-agent platform."
2. Write `task.yaml` into `workspace`:
   ```yaml
   task_id: e2e
   description: Read and summarize
   max_steps: 10
   model_route: "mock-model"
   tools: [read_file, write_file]
   instructions: Read ARCHITECTURE.md, write a summary to REPORT.md.
   outputs:
     files: [REPORT.md]
     assertions:
       - "file_exists:REPORT.md"
       - "file_contains:REPORT.md:Apex"
   ```
3. Build env map: `{"APEX_LLM_BASE_URL": "http://localhost:" + server.getAddress().getPort() + "/v1", "APEX_LLM_API_KEY": "test", "APEX_LLM_DEFAULT_MODEL": "mock-model", "APEX_LLM_TIMEOUT_SEC": "10"}`
4. Call `Main.run(new String[]{"--real", "--workspace", workspace.toString(), taskPath.toString()}, env, out, err)`.
5. Assert:
   - exit code == 0
   - `REPORT.md` exists in workspace
   - `REPORT.md` contains "Apex"
   - stdout contains `"status":"COMPLETED"`
   - `workspace/.apex/steps.db` exists
6. Second test: assertions fail. Task file's assertion says
   `file_contains:REPORT.md:NOT_PRESENT`. Assert exit code 1 and
   stdout contains `"status":"ASSERTION_FAILED"`.

Stop server in `@AfterEach`.

## 6. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `E2ETest` passes both tests, including the full
      `Main.run("--real", …)` invocation.
- [ ] The E2E test proves: task load → model call → tool execute →
      step record → assertion check → return.
- [ ] `RealTaskRunnerTest` passes all 8 tests.
- [ ] `LlmResponseParserTest` passes all 10 tests.
- [ ] `AssertionCheckerTest` passes all 7 tests.
- [ ] `Main` supports `--real`, `--workspace`, and default stub mode.
- [ ] S2.6's `MainTest` still passes (default stub mode unchanged).
- [ ] No file exceeds 200 lines.
- [ ] No enterprise dependency added beyond S2.4/S2.5/S2.6.
- [ ] `docs/INVARIANTS.md` unchanged.
- [ ] No changes to `engine-tools-impl`, `engine-llm-http`,
      `engine-store-sqlite`, `engine-planner`, or `engine-core`.
- [ ] No spec files read or created.

## 7. Do NOT add

- Any Spring, Google, Embabel, Jackson, Micrometer dependency
- Any change to the four Tier 1 adapter modules or the two Tier 0
  modules
- Any Layer 1 spec type usage
- Any use of `AStarPlanner` in this phase
- Any retry logic on model failures
- Any live network call in tests — mock server only
- Any assertion prefix beyond `file_exists:` and `file_contains:`
- Any file not listed in §4 or §5

## 8. Commit

Four commits on `phase/s2.7-e2e`:

1. `feat(S2.7): add Decision, LlmResponseParser, PromptBuilder`
2. `feat(S2.7): add AssertionChecker`
3. `feat(S2.7): add RealTaskRunner and wire Main --real / --workspace`
4. `test(S2.7): add unit tests and E2E integration test`

Push. Report CI link, per-test-class pass counts, and confirm the
`E2ETest` both tests passed. This is the **S2 milestone**. If CI fails
twice on the same root cause, stop and report the log. When green and
merged, the kernel is self-hosting.

---

**End of S2.7 session package.**
