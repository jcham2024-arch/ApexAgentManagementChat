# PHASE-S3.3-AUDIT-CLI.md — Session Package, Part 5 of 8

**Version:** 1.0 · **Part 5 of 8:** §6.1–§6.3, RealTaskRunner, Main, README.
Parts: SESSION · CORE-PORT · AUDIT-CORE · AUDIT-LOG · CLI · AUDIT-TESTS ·
RUNNER-TESTS · CRITERIA, each `PHASE-S3.3-AUDIT-<part>.md`
(`AGENT_RULES.md` §2.13).

---
## 6. Files — `engine-cli`

### 6.1 `engine-cli/src/main/java/com/apex/engine/cli/RealTaskRunner.java`

Replace the entire file:

```java
package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.ToolExecutorPort;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.core.port.model.StepLogEntry;
import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Real task runner. ReAct loop over the model, tools, storage, and
 * audit. Every tool call is audited before execution (one entry per
 * call). Arguments are not recorded in the audit — they may contain
 * secrets the redactor might miss.
 */
public final class RealTaskRunner implements TaskRunner {

    private final ChatModelPort model;
    private final ToolExecutorPort tools;
    private final StoragePort storage;
    private final AuditPort audit;
    private final TenantId tenantId;
    private final Path workspace;
    private final PromptBuilder prompts = new PromptBuilder();
    private final LlmResponseParser parser = new LlmResponseParser();
    private final AssertionChecker checker = new AssertionChecker();

    public RealTaskRunner(ChatModelPort model, ToolExecutorPort tools,
                          StoragePort storage, AuditPort audit,
                          TenantId tenantId, Path workspace) {
        this.model = Objects.requireNonNull(model, "model");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.audit = Objects.requireNonNull(audit, "audit");
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

### 6.2 `engine-cli/src/main/java/com/apex/engine/cli/Main.java`

Three edits to the existing file.

**Edit 1:** Add imports:
```java
import com.apex.engine.core.port.AuditPort;
import com.apex.platform.audit.InMemoryAuditLog;
import java.nio.charset.StandardCharsets;
```

**Edit 2:** In `buildRealRunner`, after `StoragePort storage = ...`, add:

```java
byte[] auditKey = env.containsKey("APEX_AUDIT_SECRET")
        ? env.get("APEX_AUDIT_SECRET").getBytes(StandardCharsets.UTF_8)
        : DEV_AUDIT_KEY;
AuditPort audit = new InMemoryAuditLog(auditKey);
```

**Edit 3:** Change the `RealTaskRunner` construction from:

```java
return new RealTaskRunner(model, tools, storage, tenant, workspace);
```

to:

```java
return new RealTaskRunner(model, tools, storage, audit, tenant, workspace);
```

**Edit 4:** Add the `DEV_AUDIT_KEY` constant at the class level, with a
comment marking it as dev-only:

```java
/**
 * Development-only audit key. Production MUST set APEX_AUDIT_SECRET.
 * This 32-byte array is public and provides no tamper-evidence against
 * an attacker with read access to the source.
 */
private static final byte[] DEV_AUDIT_KEY = (
        "dev-only-do-not-use-in-production!!").getBytes(StandardCharsets.UTF_8);
```

The string literal is exactly 32 bytes. If it were shorter, `HashChain`
would reject it at construction.

### 6.3 `engine-cli/README.md`

Add a section after "## Tenant":

```markdown
## Audit

Every tool call is recorded in a hash-chained audit log. Production
deployments MUST set `APEX_AUDIT_SECRET` to a value at least 32 bytes
long. Without it, the CLI uses a hardcoded development key — the chain
verifies, but against a public secret, so tampering cannot be
detected. The audit log is in-memory only in S3.3; file persistence
lands in S6.
```
