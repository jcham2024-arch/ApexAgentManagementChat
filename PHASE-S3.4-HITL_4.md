# PHASE-S3.4-HITL.md

**Version:** 1.0 · **Sub-phase of S3** · **Ends with human-in-the-loop approval gating for L3+ tool calls.**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**

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