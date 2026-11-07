# PHASE-S3.4-HITL.md

**Version:** 1.0 · **Sub-phase of S3** · **Ends with human-in-the-loop approval gating for L3+ tool calls.**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**
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