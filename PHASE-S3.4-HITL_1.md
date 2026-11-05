# PHASE-S3.4-HITL.md

**Version:** 1.0 · **Sub-phase of S3** · **Ends with human-in-the-loop approval gating for L3+ tool calls.**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**
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