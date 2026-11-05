# PHASE-S3.4-HITL.md

**Version:** 1.0 · **Sub-phase of S3** · **Ends with L3+ tool calls
blocking on `ApprovalPort` and L0–L2 not blocking**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S3.4

Authoritative where they conflict with any prior file. Do not stop over
them.

### 1.1 `RealTaskRunner` constructor changes — breaking

S3.3 defined:
```java
RealTaskRunner(ChatModelPort, ToolExecutorPort, StoragePort,
               AuditPort, TenantId, Path)
```

S3.4 adds two parameters:
```java
RealTaskRunner(ChatModelPort, ToolExecutorPort, StoragePort,
               AuditPort, ApprovalPort, Map<String, RiskLevel>,
               TenantId, Path)
```

Every construction site must be updated: `Main.buildRealRunner`,
`RealTaskRunnerTest`, `RealTaskRunnerAuditTest`.

### 1.2 `Main.run` gains an `InputStream` parameter — breaking

Current signature:
```java
static int run(String[] args, Map<String, String> env,
               PrintStream out, PrintStream err)
```

New signature:
```java
static int run(String[] args, Map<String, String> env,
               InputStream in, PrintStream out, PrintStream err)
```

`main()` passes `System.in`. Tests pass `new ByteArrayInputStream(...)`.
Every call site updated: `MainTest` (5 tests), `E2ETest` (2 tests).

### 1.3 Risk registry is a `Map<String, RiskLevel>`, not a port

The `ToolExecutorPort` interface (S2.1) has one method `execute(ToolCall)`.
Adding a `riskLevel(String)` method would be a port signature change
with cross-module fallout. Instead, `RealTaskRunner` takes a plain
`Map<String, RiskLevel>` in its constructor. Unknown tool names default
to `L3` (fail-safe: unregistered tools require approval).

The default registry lives in `Main`. Tests provide their own.

### 1.4 Audit happens before approval

The audit entry `"tool_call:<name>"` is written unconditionally before
the approval check. A denied attempt is still recorded as an
attempted call — this is what an audit log is for. The denial itself
is not separately audited in S3.4; the model's next decision carries
the consequence, and `step_log` captures the outcome. Adding an
explicit `"tool_denied"` audit entry is deferred to a later phase.

### 1.5 Denials are fed back to the model, not fatal

If `ApprovalPort` returns denied, the runner passes a denial message
to the model as a tool result and continues the loop. The model can
try a different action. If it repeatedly tries the same denied action,
`max_steps` terminates the run. This is more flexible than a hard
abort — the model gets feedback and can adapt.

### 1.6 `TerminalApproval` is the default; `AutoApprove` is opt-in via `--auto-approve`

`Main` uses `TerminalApproval` unless `--auto-approve` is passed.
`TerminalApproval` reads from the `InputStream` passed to `Main.run`
and prompts via the `PrintStream err` (stdout stays JSON-only). On EOF
or `IOException`, it returns denial — fail-safe.

### 1.7 No approval prompt for L0–L2

`RiskLevel.requiresApproval()` returns true for L3, L4, L5 only. The
runner calls `approval.request(...)` only if that method returns true.

### 1.8 Spec not required

Router §4 confirms S3.4 does not need `docs/apex-chat-spec/`. Do not
read spec parts. Do not create spec files.

### 1.9 No `docs/INVARIANTS.md` change

E2 ("Capability escalation requires grant") is proven in S3.5, not
here. HITL gating is an S3 exit criterion (router §5.B), not a named
invariant.

## 2. Prerequisite

S3.3 merged to `main`. `mvn -B test` green. `AuditPort` has the new
three-method signature; `AuditEntry` has `previousHash`;
`RealTaskRunner` takes `AuditPort`. If any is false, stop and report.

## 3. Scope

Deliver:

1. `RealTaskRunner`: new constructor params, approval gate before L3+
   tool execution
2. `Main`: `--auto-approve` flag, `InputStream` param, risk registry
3. Three new files in `engine-cli`: `TerminalApproval`, `AutoApprove`,
   `AutoDeny`
4. `engine-cli/pom.xml`: explicit dependency on `engine-planner`
5. Test updates: `MainTest`, `E2ETest`, `RealTaskRunnerTest`,
   `RealTaskRunnerAuditTest`
6. New test: `RealTaskRunnerApprovalTest`, `TerminalApprovalTest`
7. `engine-cli/README.md`: document `--auto-approve` and risk levels
8. **No** `docs/INVARIANTS.md` change

## 4. Files — `engine-cli` production

### 4.1 `engine-cli/pom.xml`

Add to `<dependencies>` after `apex-chat-common`:

```xml
<dependency>
    <groupId>com.apex</groupId>
    <artifactId>engine-planner</artifactId>
    <version>${project.version}</version>
</dependency>
```

`RiskLevel` is used directly in `RealTaskRunner` and `Main`, so this
dependency must be explicit.

### 4.2 `engine-cli/src/main/java/com/apex/engine/cli/TerminalApproval.java`

```java
package com.apex.engine.cli;

import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Reads an approval decision from a terminal-style prompt. Prompts via
 * {@code err} (stdout stays JSON-only). Accepts {@code y}/{@code Y} or
 * any string starting with those; everything else (including blank
 * input and EOF) is denial.
 *
 * <p>On EOF or IOException, returns denial — fail-safe. On no TTY
 * (running under CI), the caller should pass an {@link AutoApprove} or
 * {@link AutoDeny} explicitly; this class does not detect TTY.
 */
public final class TerminalApproval implements ApprovalPort {

    private final BufferedReader in;
    private final PrintStream prompt;

    public TerminalApproval(InputStream in, PrintStream prompt) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(prompt, "prompt");
        this.in = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        this.prompt = prompt;
    }

    @Override
    public ApprovalDecision request(ApprovalRequest req) {
        Objects.requireNonNull(req, "req");
        prompt.println("=== APPROVAL REQUIRED ===");
        prompt.println("Tool: " + req.summary());
        prompt.println("Risk: " + req.riskLevel());
        prompt.print("Approve? [y/N]: ");
        prompt.flush();
        try {
            String line = in.readLine();
            if (line == null) {
                return new ApprovalDecision(req.id(), false,
                        "no input (EOF)");
            }
            String trimmed = line.trim();
            boolean approved = trimmed.startsWith("y")
                    || trimmed.startsWith("Y");
            return new ApprovalDecision(req.id(), approved,
                    approved ? "user-approved" : "user-denied");
        } catch (IOException e) {
            return new ApprovalDecision(req.id(), false,
                    "IO error: " + e.getMessage());
        }
    }
}
```

### 4.3 `engine-cli/src/main/java/com/apex/engine/cli/AutoApprove.java`

```java
package com.apex.engine.cli;

import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;

import java.util.Objects;

/**
 * Approval port that approves every request without prompting. Used by
 * {@code --auto-approve} and by tests that need to prove the tool path
 * without interactive input. Not for production use without an
 * external policy layer.
 */
public final class AutoApprove implements ApprovalPort {

    @Override
    public ApprovalDecision request(ApprovalRequest req) {
        Objects.requireNonNull(req, "req");
        return new ApprovalDecision(req.id(), true, "auto-approved");
    }
}
```

### 4.4 `engine-cli/src/main/java/com/apex/engine/cli/AutoDeny.java`

```java
package com.apex.engine.cli;

import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;

import java.util.Objects;

/**
 * Approval port that denies every request without prompting. Used by
 * tests that prove denial behaviour. May also be useful operationally
 * when an operator wants to observe the model's reaction to refusal
 * before enabling interactive approval.
 */
public final class AutoDeny implements ApprovalPort {

    @Override
    public ApprovalDecision request(ApprovalRequest req) {
        Objects.requireNonNull(req, "req");
        return new ApprovalDecision(req.id(), false, "auto-denied");
    }
}
```

### 4.5 `engine-cli/src/main/java/com/apex/engine/cli/RealTaskRunner.java`

Replace the entire file:

```java
package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.ToolExecutorPort;
import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.core.port.model.StepLogEntry;
import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;
import com.apex.engine.planner.RiskLevel;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Real task runner. ReAct loop over the model, tools, storage, audit,
 * and approval. Every tool call is audited before execution. L3+ tools
 * require approval first; denials are fed back to the model as tool
 * results so it can adapt. Unknown tools default to L3 (fail-safe:
 * approval required).
 */
public final class RealTaskRunner implements TaskRunner {

    private final ChatModelPort model;
    private final ToolExecutorPort tools;
    private final StoragePort storage;
    private final AuditPort audit;
    private final ApprovalPort approval;
    private final Map<String, RiskLevel> riskRegistry;
    private final TenantId tenantId;
    private final Path workspace;
    private final PromptBuilder prompts = new PromptBuilder();
    private final LlmResponseParser parser = new LlmResponseParser();
    private final AssertionChecker checker = new AssertionChecker();

    public RealTaskRunner(ChatModelPort model, ToolExecutorPort tools,
                          StoragePort storage, AuditPort audit,
                          ApprovalPort approval,
                          Map<String, RiskLevel> riskRegistry,
                          TenantId tenantId, Path workspace) {
        this.model = Objects.requireNonNull(model, "model");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.approval = Objects.requireNonNull(approval, "approval");
        this.riskRegistry = Map.copyOf(riskRegistry);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    @Override
    public TaskRunResult run(TaskDefinition task) {
        String sessionId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        List<String> history = prompts.initialParts(task);

        for (int step = 0; step < task.maxSteps(); step++) {
            long stepStart = System.currentTimeMillis();
            ChatResponse resp;
            try {
                resp = model.chat(new ChatRequest(task.modelRoute(), history));
            } catch (RuntimeException e) {
                return new TaskRunResult(task.taskId(), "FAILED",
                        step, elapsed(start));
            }

            Decision decision;
            try {
                decision = parser.parse(resp.content());
            } catch (RuntimeException e) {
                return new TaskRunResult(task.taskId(), "PARSE_ERROR",
                        step, elapsed(start));
            }

            storage.appendStep(new StepLogEntry(
                    UUID.randomUUID().toString(),
                    tenantId,
                    sessionId, step,
                    decision instanceof Decision.Done ? "finish" : "action",
                    System.currentTimeMillis() - stepStart));

            if (decision instanceof Decision.Done) {
                List<AssertionChecker.CheckResult> checks =
                        checker.check(workspace, task);
                boolean ok = checker.allPassed(checks);
                return new TaskRunResult(task.taskId(),
                        ok ? "COMPLETED" : "ASSERTION_FAILED",
                        step + 1, elapsed(start));
            }

            Decision.Action action = (Decision.Action) decision;
            audit.append("tool_call:" + action.toolName(), Instant.now());

            RiskLevel risk = riskRegistry.getOrDefault(
                    action.toolName(), RiskLevel.L3);
            if (risk.requiresApproval() && !approvalGranted(action, risk)) {
                history = prompts.withToolResult(history,
                        "ERROR: approval denied for tool '"
                        + action.toolName() + "'");
                continue;
            }

            ToolResult result = tools.execute(new ToolCall(
                    UUID.randomUUID().toString(),
                    action.toolName(), action.argumentsJson()));

            history = result.isError()
                    ? prompts.withToolResult(history,
                            "ERROR: " + result.output())
                    : prompts.withToolResult(history, result.output());
        }
        return new TaskRunResult(task.taskId(), "MAX_STEPS",
                task.maxSteps(), elapsed(start));
    }

    private boolean approvalGranted(Decision.Action action, RiskLevel risk) {
        try {
            ApprovalDecision decision = approval.request(
                    new ApprovalRequest(
                            UUID.randomUUID().toString(),
                            "tool_call:" + action.toolName(),
                            risk));
            return decision.approved();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static long elapsed(long startMs) {
        return System.currentTimeMillis() - startMs;
    }
}
```

### 4.6 `engine-cli/src/main/java/com/apex/engine/cli/Main.java`

Four edits to the existing file.

**Edit 1 — imports.** Add:
```java
import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.planner.RiskLevel;
import java.io.InputStream;
import java.util.Map;
```

(Verify `java.util.Map` isn't already imported at the top.)

**Edit 2 — `main()` signature change.** Change:
```java
int code = run(args, System.getenv(), System.out, System.err);
```
to:
```java
int code = run(args, System.getenv(), System.in,
        System.out, System.err);
```

**Edit 3 — `run(...)` signature and body.** Replace the signature:
```java
static int run(String[] args, Map<String, String> env,
               InputStream in, PrintStream out, PrintStream err)
```

After `Args parsed = Args.parse(args);` and before resolving the
tenant, resolve the approval port:

```java
ApprovalPort approval = parsed.autoApprove()
        ? new AutoApprove()
        : new TerminalApproval(in, err);
```

Pass `approval` to `buildRealRunner`. Update `buildRealRunner` signature to include `ApprovalPort approval` and pass it into `RealTaskRunner`:

```java
private static TaskRunner buildRealRunner(TaskDefinition task,
                                          Map<String, String> env,
                                          Path workspace,
                                          TenantId tenant,
                                          ApprovalPort approval,
                                          PrintStream err) {
    // ... existing setup ...
    Map<String, RiskLevel> risks = defaultRiskRegistry();
    return new RealTaskRunner(model, tools, storage, audit, approval,
            risks, tenant, workspace);
}
```

Add the `defaultRiskRegistry()` helper:

```java
private static Map<String, RiskLevel> defaultRiskRegistry() {
    return Map.of(
            "read_file",   RiskLevel.L0,
            "list_files",  RiskLevel.L0,
            "write_file",  RiskLevel.L1,
            "http_get",    RiskLevel.L2,
            "run_command", RiskLevel.L4);
}
```

**Edit 4 — `Args` record gains `autoApprove` and parse branch.** Replace the `Args` record:

```java
record Args(boolean real, boolean autoApprove, Path workspace,
            TenantId tenant, Path taskFile) {
    static Args parse(String[] args) {
        boolean real = false;
        boolean autoApprove = false;
        Path workspace = Path.of(".").toAbsolutePath().normalize();
        String tenantStr = null;
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--real")) { real = true; }
            else if (a.equals("--auto-approve")) { autoApprove = true; }
            else if (a.equals("--workspace")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--workspace needs a path");
                }
                workspace = Path.of(args[++i]).toAbsolutePath().normalize();
            } else if (a.equals("--tenant")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--tenant needs an id");
                }
                tenantStr = args[++i];
            } else if (a.startsWith("--")) {
                throw new IllegalArgumentException("unknown flag: " + a);
            } else {
                positional.add(a);
            }
        }
        if (positional.size() != 1) {
            throw new IllegalArgumentException(
                    "expected 1 task file, got " + positional.size());
        }
        TenantId tenant = tenantStr != null ? new TenantId(tenantStr) : null;
        return new Args(real, autoApprove, workspace, tenant,
                Path.of(positional.get(0)));
    }
}
```

**Edit 5 — `USAGE` string.** Change to:
```java
private static final String USAGE =
        "usage: apex-cli [--real] [--auto-approve] "
        + "[--workspace <path>] [--tenant <id>] <task-file>\n";
```

### 4.7 `engine-cli/README.md`

Add a section after "## Audit":

```markdown
## Human-in-the-loop approval

L3+ tools (per `RiskLevel.requiresApproval()`) require approval before
execution. The approval port is chosen by flag:

- `--auto-approve` — every L3+ request is approved (CI, scripted runs)
- default — interactive prompt on the terminal; reads from stdin,
  writes to stderr; EOF or IO error denies

Risk levels are hardcoded for now:
| Tool | Risk |
|---|---|
| `read_file` | L0 |
| `list_files` | L0 |
| `write_file` | L1 |
| `http_get` | L2 |
| `run_command` | L4 |

Unknown tools default to L3 (approval required). Tool-specific risk
configuration lands in a later phase.
```

## 5. Test files

### 5.1 `engine-cli/src/test/java/com/apex/engine/cli/MainTest.java`

Every `Main.run(...)` call gains a third argument
`new ByteArrayInputStream(new byte[0])`. That is five call sites.
No other change.

Add imports:
```java
import java.io.ByteArrayInputStream;
```

### 5.2 `engine-cli/src/test/java/com/apex/engine/cli/E2ETest.java`

`Main.run(...)` call gains a third argument
`new ByteArrayInputStream(new byte[0])`.

Since the E2E task uses only `read_file` (L0) and `write_file` (L1),
approval is never invoked — the empty stdin is safe. No `--auto-approve`
flag is needed. Add the import.

### 5.3 `engine-cli/src/test/java/com/apex/engine/cli/RealTaskRunnerTest.java`

Constructor change. Add at the top of the class:

```java
private static final ApprovalPort APPROVE = new AutoApprove();
private static final Map<String, RiskLevel> RISKS = Map.of(
        "read_file",  RiskLevel.L0,
        "write_file", RiskLevel.L1,
        "list_files", RiskLevel.L0);
```

Every `new RealTaskRunner(...)` gains two arguments: `APPROVE` and
`RISKS`, before `tenant` and `workspace`.

Add imports:
```java
import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.planner.RiskLevel;
import java.util.Map;
```

No other test logic changes — existing tests use L0/L1 tools, so no
approval is requested.

### 5.4 `engine-cli/src/test/java/com/apex/engine/cli/RealTaskRunnerAuditTest.java`

Same constructor change as `RealTaskRunnerTest`. Define `APPROVE` and
`RISKS` constants. Update the two `new RealTaskRunner(...)` call sites.

### 5.5 New: `engine-cli/src/test/java/com/apex/engine/cli/RealTaskRunnerApprovalTest.java`

~~~java
package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.planner.RiskLevel;
import com.apex.engine.store.sqlite.SqliteStoragePort;
import com.apex.engine.tools.impl.KernelToolExecutor;
import com.apex.engine.tools.impl.RealPathJail;
import com.apex.platform.audit.InMemoryAuditLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RealTaskRunnerApprovalTest {

    private static final TenantId TENANT = new TenantId("t-hitl");
    private static final byte[] AUDIT_KEY = new byte[32];
    private static final Map<String, RiskLevel> RISKS = Map.of(
            "read_file",   RiskLevel.L0,
            "run_command", RiskLevel.L4);

    static { java.util.Arrays.fill(AUDIT_KEY, (byte) 0x42); }

    @TempDir Path tempDir;
    RealPathJail jail;
    InMemoryAuditLog audit;
    Path echoBinary;

    @BeforeEach
    void setUp() throws Exception {
        echoBinary = findEcho();
        jail = new RealPathJail(tempDir);
        audit = new InMemoryAuditLog(AUDIT_KEY);
    }

    @Test
    void l0ToolDoesNotRequireApproval() {
        // read_file is L0; AutoDeny must not be consulted.
        RealTaskRunner runner = buildRunner(
                List.of(
                        "{\"action\":\"read_file\",\"arguments\":{\"path\":\"a.txt\"}}",
                        "{\"done\":true,\"summary\":\"ok\"}"),
                new AutoDeny());
        FilesWrite("a.txt", "hi");
        TaskRunResult result = runner.run(task("t1", "read_file"));
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    void l4ToolBlocksWhenApprovalDenied() throws Exception {
        RealTaskRunner runner = buildRunner(
                List.of(
                        "{\"action\":\"run_command\",\"arguments\":{\"cmd\":\"echo\",\"args\":[\"hi\"]}}",
                        "{\"done\":true,\"summary\":\"gave up\"}"),
                new AutoDeny());
        TaskRunResult result = runner.run(task("t2", "run_command"));
        // Model saw denial, gave up; task completes without executing the tool.
        assertThat(result.status()).isEqualTo("COMPLETED");
        // Audit records the attempted call regardless of approval outcome.
        assertThat(audit.all())
                .extracting(e -> e.action())
                .contains("tool_call:run_command");
    }

    @Test
    void l4ToolExecutesWhenApprovalGranted() throws Exception {
        RealTaskRunner runner = buildRunner(
                List.of(
                        "{\"action\":\"run_command\",\"arguments\":{\"cmd\":\"echo\",\"args\":[\"hi\"]}}",
                        "{\"done\":true,\"summary\":\"ran\"}"),
                new AutoApprove());
        TaskRunResult result = runner.run(task("t3", "run_command"));
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(audit.all())
                .extracting(e -> e.action())
                .contains("tool_call:run_command");
    }

    @Test
    void unknownToolDefaultsToL3AndRequiresApproval() {
        // Registry does not contain "mystery"; default to L3, so AutoDeny
        // produces denial, model sees it, gives up.
        RealTaskRunner runner = buildRunner(
                List.of(
                        "{\"action\":\"mystery\",\"arguments\":{}}",
                        "{\"done\":true,\"summary\":\"gave up\"}"),
                new AutoDeny());
        TaskRunResult result = runner.run(task("t4", "mystery"));
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    void repeatedDenialsTerminateAtMaxSteps() {
        // Model keeps trying the same L4 tool; AutoDeny refuses every time.
        RealTaskRunner runner = buildRunner(
                List.of("{\"action\":\"run_command\",\"arguments\":{\"cmd\":\"echo\",\"args\":[\"x\"]}}"),
                new AutoDeny());
        TaskRunResult result = runner.run(task("t5", "run_command", 3));
        assertThat(result.status()).isEqualTo("MAX_STEPS");
        assertThat(result.steps()).isEqualTo(3);
    }

    private RealTaskRunner buildRunner(List<String> responses,
                                       ApprovalPort approval) {
        KernelToolExecutor tools = new KernelToolExecutor(
                jail, Map.of("echo", echoBinary));
        SqliteStoragePort storage = new SqliteStoragePort(
                tempDir.resolve("steps.db"));
        ChatModelPort model = new Scripted(responses);
        return new RealTaskRunner(model, tools, storage, audit, approval,
                RISKS, TENANT, tempDir);
    }

    private void FilesWrite(String name, String content) {
        try {
            Files.writeString(tempDir.resolve(name), content);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private TaskDefinition task(String id, String tool) {
        return task(id, tool, 5);
    }

    private TaskDefinition task(String id, String tool, int maxSteps) {
        return new TaskDefinition(id, "hitl test", "do it", maxSteps,
                "m", List.of(tool), List.of(), List.of(), List.of());
    }

    private static Path findEcho() {
        for (String c : new String[]{"/bin/echo", "/usr/bin/echo"}) {
            Path p = Path.of(c);
            if (Files.isExecutable(p)) return p;
        }
        throw new IllegalStateException("echo not found");
    }

    private record Scripted(List<String> responses) implements ChatModelPort {
        private static final AtomicInteger NEXT = new AtomicInteger();
        @Override
        public ChatResponse chat(ChatRequest req) {
            return new ChatResponse(responses.get(
                    Math.min(NEXT.getAndIncrement(), responses.size() - 1)));
        }
    }
}