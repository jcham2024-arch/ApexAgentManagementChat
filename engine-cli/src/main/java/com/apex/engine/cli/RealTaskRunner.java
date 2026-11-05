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
            ChatResponse resp = runModelChat(task, history);
            if (resp == null) continue;

            Decision decision = parseDecision(resp.content());
            if (decision == null) continue;

            storage.appendStep(new StepLogEntry(
                    UUID.randomUUID().toString(),
                    tenantId,
                    sessionId, step,
                    decision instanceof Decision.Done ? "finish" : "action",
                    System.currentTimeMillis() - stepStart));

            if (decision instanceof Decision.Done) {
                return runAssertions(workspace, task, step, start);
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

    private ChatResponse runModelChat(TaskDefinition task, List<String> history) {
        try {
            return model.chat(new ChatRequest(task.modelRoute(), history));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Decision parseDecision(String content) {
        try {
            return parser.parse(content);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private TaskRunResult runAssertions(Path workspace, TaskDefinition task, int step, long start) {
        List<AssertionChecker.CheckResult> checks =
                checker.check(workspace, task);
        boolean ok = checker.allPassed(checks);
        return new TaskRunResult(task.taskId(),
                ok ? "COMPLETED" : "ASSERTION_FAILED",
                step + 1, elapsed(start));
    }
}

    private static long elapsed(long startMs) {
        return System.currentTimeMillis() - startMs;
    }
}
