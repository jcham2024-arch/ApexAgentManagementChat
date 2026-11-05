# PHASE-S3.2-TENANT-POMS-RUNNER.md — Session Package, Part 4 of 7

**Version:** 1.0 · **Part 4 of 7:** §4.4–§4.5, store-sqlite pom and RealTaskRunner.
Parts: SESSION · CORE-PORT · SQLITE · POMS-RUNNER · CLI · TESTS ·
ISOLATION-CRITERIA, each `PHASE-S3.2-TENANT-<part>.md` (`AGENT_RULES.md`
§2.13).

---
### 4.4 `engine-store-sqlite/pom.xml` — add `apex-chat-common`

Insert inside `<dependencies>`, after the `engine-core` dependency:

```xml
<dependency>
    <groupId>com.apex</groupId>
    <artifactId>apex-chat-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

No other dependency change.

### 4.5 `engine-cli/src/main/java/com/apex/engine/cli/RealTaskRunner.java`

Replace the entire file with:

```java
package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.ToolExecutorPort;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.core.port.model.StepLogEntry;
import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Real task runner. ReAct loop over the model, tools, and storage.
 * Every {@link StepLogEntry} written carries the runner's tenant, so
 * cross-tenant queries cannot surface this run's steps.
 */
public final class RealTaskRunner implements TaskRunner {

    private final ChatModelPort model;
    private final ToolExecutorPort tools;
    private final StoragePort storage;
    private final TenantId tenantId;
    private final Path workspace;
    private final PromptBuilder prompts = new PromptBuilder();
    private final LlmResponseParser parser = new LlmResponseParser();
    private final AssertionChecker checker = new AssertionChecker();

    public RealTaskRunner(ChatModelPort model, ToolExecutorPort tools,
                          StoragePort storage, TenantId tenantId,
                          Path workspace) {
        this.model = Objects.requireNonNull(model, "model");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.storage = Objects.requireNonNull(storage, "storage");
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
            ToolResult result = tools.execute(new ToolCall(
                    UUID.randomUUID().toString(),
                    action.toolName(), action.argumentsJson()));

            history = result.isError()
                    ? prompts.withToolResult(history, "ERROR: " + result.output())
                    : prompts.withToolResult(history, result.output());
        }
        return new TaskRunResult(task.taskId(), "MAX_STEPS",
                task.maxSteps(), elapsed(start));
    }

    private static long elapsed(long startMs) {
        return System.currentTimeMillis() - startMs;
    }
}
```
