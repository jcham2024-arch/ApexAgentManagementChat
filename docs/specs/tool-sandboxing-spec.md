# BUILD-SPEC: Tool Sandboxing and Capability Restrictions (`engine-tools-impl`)

## 1. Module Identity

| Field | Value |
| :--- | :--- |
| **Module name** | `engine-tools-impl` (existing Tier 1 module, shell → impl) |
| **Repository location** | `project/engine-tools-impl/` |
| **Parent aggregator** | `project/apex-core/pom.xml` |
| **Tier** | **Tier 1** (adapter — tool implementations with I/O) |
| **Governed by** | ADR-007 (Tool sandboxing and capability restrictions) |
| **Spike input** | `docs/spikes/A7-tooling.md` |
| **Decision input** | `docs/contracts/decisions/Q15-Q16-tool-sandboxing.md` |
| **Spec readiness** | **READY** — Q15/Q16 closed, ADR-007 accepted, port classification unchanged |

## 2. Purpose

Implement v1 kernel tools (`read`, `write`, `list`, `http`, `run`) in
`engine-tools-impl`, sandboxed behind a deny-by-default capability manifest.
Tool implementations are Tier 1 (I/O allowed); `engine-tools` (Tier 0)
hosts tool metadata and policy records only.

## 3. Ports Consumed and Provided

### Consumed (Tier 0 contracts)

| Port | Package | Used as |
|------|---------|---------|
| `ToolExecutorPort` | `com.apex.engine.core.port` | `ToolResult execute(ToolCall)` — implemented per tool |
| `ToolPolicy` | `com.apex.engine.core.port` | Default-deny policy consulted by `ToolGatewayPort` |
| `ToolGatewayPort` | `com.apex.engine.core.port` | Route tool calls through gateway (deny → `GatewayDecision.Deny`; require approval → `GatewayDecision.RequireApproval`; approve → execute) |
| `AuditPort` | `com.apex.engine.core.port` | Record every tool invocation and denial |

### Provided (Tier 1 implementations)

| Implementation | Purpose |
|---------------|---------|
| `ReadTool` | `read` — read-only paths, path-traversal blocked |
| `WriteTool` | `write` — write-only paths, no exec bits, size cap |
| `ListTool` | `list` — directory listing only, depth cap |
| `HttpTool` | `http` — allow-listed hosts, timeout, no private IPs |
| `RunTool` | `run` — command allow-list, no shell interpolation, timeout |
| `CapabilityManifest` | Per-agent manifest: allowed tools, hosts, read paths |
| `ToolGate` | Deny-by-default gate; deny-wins evaluation |

### Port classification

- **`ToolExecutorPort`**: **unchanged** — `execute(ToolCall)` signature unchanged.
- **`ToolPolicy`**: **unchanged** — default-deny policy interface.
- **`ToolGatewayPort`**: **unchanged** — `execute(AuthorizationRequest)` signature unchanged.
- **`AuditPort`**: **unchanged** — invoked to record invocations and denials.
- **No new Tier 0 ports introduced.**

## 4. Public API Surface and Compatibility

### Java API

```java
// Capability manifest (Tier 1)
public record CapabilityManifest(
    Set<String> allowedTools,       // empty = nothing permitted
    Set<String> allowedHosts,       // empty = nothing permitted
    Set<String> allowedReadPaths    // empty = nothing permitted
) {}

// Tool gate (Tier 1)
public interface ToolGate {
    GatewayDecision authorize(ToolCall call, CancellationToken token);
    void recordInvocation(ToolCall call, ToolResult result, boolean denied);
}

// Tool implementations (Tier 1)
public class ReadTool implements ToolExecutorPort { ... }
public class WriteTool implements ToolExecutorPort { ... }
public class ListTool implements ToolExecutorPort { ... }
public class HttpTool implements ToolExecutorPort { ... }
public class RunTool implements ToolExecutorPort { ... }
```

### Compatibility statement

- **`ToolExecutorPort.execute(ToolCall)`**: unchanged — implementations
  conform to the existing interface.
- **`ToolGatewayPort.execute(AuthorizationRequest)`**: unchanged — the
  gateway consults `ToolGate` for authorization decisions.
- **`AuditPort`**: unchanged — invoked to record invocations and denials.
- **No breaking changes** to any Tier 0 port interface.

## 5. Dependency Ceiling and Justification

| Dependency | Scope | Justification |
| :--- | :--- | :--- |
| `com.apex:engine-core` | Compile | Tier 0 ports and `ToolExecutorPort`, `ToolCall`, `ToolResult` (project dependency). |
| `com.apex:engine-tools` | Compile | Tool metadata and policy records (project dependency, Tier 0). |
| `org.jspecify` | Compile | Null-safety annotations per project standards. |
| `org.junit.jupiter:junit-jupiter` | Test | Test framework per project standard. |
| `org.assertj:assertj-core` | Test | Assertions per project standard. |
| `com.tngtech.archunit:archunit-junit5` | Test | Architectural enforcement per project standard. |

**No new external dependencies.** All dependencies are project-internal or
already-approved.

**Tier 0 purity:** `engine-tools-impl` is Tier 1 — I/O allowed here.
`engine-tools` (Tier 0) hosts metadata/policy records only, no I/O.
`CorePurityTest` passes in all Tier 0 modules.

## 6. Implementation Design

### 6.1 Default posture

**Deny by default.** Every tool not explicitly declared in the agent's
capability manifest is rejected. Every absent manifest field means an empty
allow-list — nothing permitted.

### 6.2 Capability manifest

A per-agent manifest listing allowed tools, allowed hosts, and allowed read
paths. The manifest is compiled into the runtime's tool gate, not consulted
informally. An unenforced manifest is worse than no manifest (false
assurance).

### 6.3 Deny-wins evaluation

Deny rules take precedence over allow rules. Explicit deny rules are
evaluated independently; deny always wins across scopes.

### 6.4 Sandbox boundary

| Tool | Sandbox restriction |
|------|---------------------|
| `read` | Read-only paths; path-traversal blocked; resolve against declared root |
| `write` | Write-only paths; no exec bits; size cap; resolve against declared root |
| `list` | Directory listing only; depth cap |
| `http` | Allow-listed hosts; timeout; no local/link-local addresses (SSRF prevention) |
| `run` | Command allow-list; no shell interpolation (`/bin/sh -c` with user input); timeout |

### 6.5 Network allow-list

Outbound HTTP restricted to an allow-list of hosts. All other egress denied
by default. Host allow-list is empty by default in v1, populated per
deployment via the capability manifest (no hardcoded production hosts).

### 6.6 Audit

Every tool invocation and every permission decision (including denials) is
logged into the hash-chained audit log via `AuditPort`. Denials are the
signal that the policy is working — they belong in the audit chain.

### 6.7 Sensitive actions

Explicit confirmation required for actions such as infrastructure changes or
pushes. A sensitive action without confirmation is blocked.

### 6.8 Timeouts and cancellation

Every tool execution bounds wall-clock time; on timeout, return
`ToolResult.failure(timeout)`. `CancellationToken` checked before starting
and at checkpoints; if cancelled, return cancelled result without side
effects where possible.

## 7. Determinism Impact

**No impact on Tier 0 determinism.** Tool execution side-effects (I/O,
HTTP, process) are nondeterministic but confined to Tier 1
(`engine-tools-impl`). `PlanLoop` remains deterministic given the same
`ToolResult` inputs — tool results are fed back as opaque values.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **Unsafe autonomy** | Prevented by deny-by-default; no full tool access "for flexibility". |
| **Unenforced manifest** | Forbidden — manifest compiled into runtime gate. |
| **Path traversal** | Blocked — file tools resolve against declared root; reject `..` |
| | and absolute paths outside root. |
| **SSRF** | Blocked — `http` tool only contacts allow-listed hosts; blocks |
| | private/link-local addresses. |
| **Shell injection** | Blocked — `run` tool uses command allow-list; no shell |
| | interpolation. |
| **Audit gap** | Closed — every invocation and denial logged via `AuditPort`. |

## 9. Audit Impact

Every tool invocation (tool name, args redacted, outcome, duration) and
every permission decision (allow or deny) is recorded via `AuditPort`. The
audit chain is tamper-evident — any post-hoc modification to an audit entry
breaks the chain.

## 10. Failure, Cancellation, and Timeout Semantics

| Scenario | Required Behavior |
| :--- | :--- |
| **Undeclared tool** | `GatewayDecision.Deny`; denial recorded in audit chain. |
| **Host outside allow-list** | `GatewayDecision.Deny`; denial recorded in audit chain. |
| **Sensitive action without confirmation** | Blocked; denial recorded in audit chain. |
| **Timeout** | `ToolResult.failure(timeout)`; no partial result visible. |
| **Cancellation** | `CancellationToken` checked before start and at checkpoints; |
| | cancelled result without side effects where possible. |
| **Tool exception** | Mapped to `ToolResult.failure(...)`; never thrown across port |
| | boundary. |

## 11. Test Plan

### 11.1 Unit tests

- `CapabilityManifest` with empty fields → nothing permitted.
- `ToolGate.authorize()` denies undeclared tool.
- `ToolGate.authorize()` allows declared tool.
- `ToolGate.authorize()` denies host outside allow-list.
- Deny-wins: explicit deny overrides allow.
- Path traversal: `read`/`write` with `..` rejected.
- SSRF: `http` to private/link-local address rejected.
- Shell injection: `run` with shell interpolation rejected.
- Timeout: `ToolResult.failure(timeout)` on tool timeout.
- Cancellation: `CancellationToken` checked before tool start.

### 11.2 Integration tests

- **Undeclared tool rejected:** A tool not in the manifest is denied
  (acceptance test minimum).
- **Declared tool allowed:** A tool in the manifest is allowed
  (acceptance test minimum).
- **Outbound host denied + audit:** An `http` call to a host outside the
  allow-list is denied, and the denial is recorded in the audit chain
  (acceptance test minimum).
- **Sensitive action blocked:** A sensitive action without confirmation is
  blocked (acceptance test minimum).
- **Every invocation audited:** Every `execute()` call produces an
  `AuditEntry` via `AuditPort`.
- **Every denial audited:** Every `GatewayDecision.Deny` produces an
  `AuditEntry` via `AuditPort`.

### 11.3 Architectural enforcement

- `CorePurityTest` passes in all Tier 0 modules.
- `engine-tools` (Tier 0) contains no I/O implementations.

### 11.4 Coverage targets

- All five tools (`read`, `write`, `list`, `http`, `run`) exercised.
- Manifest allow/deny logic exercised.
- Sandbox restrictions (path, network, shell) exercised.
- Audit recording (invocations + denials) exercised.
- Timeout and cancellation paths exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. `engine-tools-impl` builds under `mvn -B -f project/apex/pom.xml verify`.
2. Five kernel tools implemented: `read`, `write`, `list`, `http`, `run`.
3. Undeclared tool rejected by `ToolGate` (acceptance test minimum).
4. Declared tool allowed by `ToolGate` (acceptance test minimum).
5. Outbound host outside allow-list denied + denial recorded in audit chain
   (acceptance test minimum).
6. Sensitive action without confirmation blocked (acceptance test minimum).
7. Every tool invocation recorded via `AuditPort`.
8. Every permission decision (allow or deny) recorded via `AuditPort`.
9. Path traversal, SSRF, and shell injection blocked.
10. Every tool execution bounded by timeout.
11. `CancellationToken` checked before tool start and at checkpoints.
12. `CorePurityTest` passes in all Tier 0 modules.
13. Manifest is compiled into runtime gate (not just documentation).

## 13. Rollback / Disable Strategy

- **Exclude the module:** Applications that do not need tool execution
  simply do not add `engine-tools-impl` as a dependency.
- **Permissive manifest:** A manifest with all tools/hosts/paths allowed
  (opt-in, not default) for trusted environments.
- **No Tier 0 impact:** `engine-tools` (Tier 0) hosts metadata only;
  removing `engine-tools-impl` has zero effect on Tier 0.

## 14. Out of Scope (Explicit)

- Exact host/command allow-lists for v1 (Q15/Q16 — populated per
  deployment; empty by default).
- Tool result size caps and duration caps (Q17 non-blocking; deferred).
- Sandbox escape detection beyond path/network/process restrictions.
- Remote tool execution (MCP handled by separate spec).

## 15. Governance References

| Artifact | Path |
| :--- | :--- |
| Spike finding | `docs/spikes/A7-tooling.md` |
| Governing ADR | `docs/adr/ADR-007-tool-sandboxing.md` |
| Decision note | `docs/contracts/decisions/Q15-Q16-tool-sandboxing.md` |
| Port contract inventory | `docs/contracts/ports.md` (`ToolExecutorPort`, `ToolGatewayPort`, `ToolPolicy` rows) |
| NFR capture | `docs/contracts/nfr.md` §7 |
| Open questions | `docs/contracts/open-questions.md` (Q15, Q16 closed) |
| Spec readiness checklist | `docs/specs/README.md` |

**Decision governance:** Q15/Q16 (deny-by-default manifest, deny-wins
evaluation, sandbox boundary, network allow-list, audit of invocations and
denials, explicit confirmation for sensitive actions) are closed and linked
above. These decisions constrain this spec as stated in each decision note's
"Constraint on future specs" section.
