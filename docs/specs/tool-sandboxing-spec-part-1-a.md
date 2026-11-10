# BUILD-SPEC: Tool Sandboxing and Capability Restrictions (`engine-tools-impl`) — Part 1-a of 3

**Source:** docs/specs/tool-sandboxing-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 1-a of 3
**Cross-reference:** See `tool-sandboxing-spec-part-1-b.md` for sections 5-8.
**Cross-reference:** See `tool-sandboxing-spec-part-2.md` for sections 9-15.

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
| **Spec readiness** | **READY** — Q15/Q16 closed, ADR-007 accepted, port |
| | classification unchanged |

## 2. Purpose

Implement v1 kernel tools (`read`, `write`, `list`, `http`, `run`) in
`engine-tools-impl`, sandboxed behind a deny-by-default capability
manifest. Tool implementations are Tier 1 (I/O allowed); `engine-tools`
(Tier 0) hosts tool metadata and policy records only.

## 3. Ports Consumed and Provided

### Consumed (Tier 0 contracts)

| Port | Package | Used as |
|------|---------|---------|
| `ToolExecutorPort` | `com.apex.engine.core.port` | `ToolResult execute(ToolCall)` — |
| | | implemented per tool |
| `ToolPolicy` | `com.apex.engine.core.port` | Default-deny policy consulted |
| | | by `ToolGatewayPort` |
| `ToolGatewayPort` | `com.apex.engine.core.port` | Route tool calls through |
| | | gateway (deny → `GatewayDecision.Deny`; require |
| | | approval → `GatewayDecision.RequireApproval`; |
| | | approve → execute) |
| `AuditPort` | `com.apex.engine.core.port` | Record every tool invocation |
| | | and denial |

### Provided (Tier 1 implementations)

| Implementation | Purpose |
|---------------|---------|
| `ReadTool` | `read` — read-only paths, |
| | path-traversal blocked |
| `WriteTool` | `write` — write-only paths, |
| | no exec bits, size cap |
| `ListTool` | `list` — directory listing |
| | only, depth cap |
| `HttpTool` | `http` — allow-listed hosts, |
| | timeout, no private IPs |
| `RunTool` | `run` — command allow-list, |
| | no shell interpolation, |
| | timeout |
| `CapabilityManifest` | Per-agent manifest: allowed |
| | tools, hosts, read paths |
| `ToolGate` | Deny-by-default gate; |
| | deny-wins evaluation |

### Port classification

- **`ToolExecutorPort`**: **unchanged** — `execute(ToolCall)` |
| signature unchanged.
- **`ToolPolicy`**: **unchanged** — default-deny policy interface.
- **`ToolGatewayPort`**: **unchanged** — |
| `execute(AuthorizationRequest)` signature unchanged.
- **`AuditPort`**: **unchanged** — invoked to record invocations |
| and denials.
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

- **`ToolExecutorPort.execute(ToolCall)`**: unchanged — implementations |
| conform to the existing interface.
- **`ToolGatewayPort.execute(AuthorizationRequest)`**: unchanged — |
| the gateway consults `ToolGate` for authorization decisions.
- **`AuditPort`**: unchanged — invoked to record invocations and |
| denials.
- **No breaking changes** to any Tier 0 port interface.
