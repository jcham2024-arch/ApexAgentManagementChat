# PHASE-S3.4-HITL.md

**Version:** 1.0 · **Sub-phase of S3** · **Ends with human-in-the-loop approval gating for L3+ tool calls.**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**
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