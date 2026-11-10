# BUILD-SPEC: Spring AI Model Provider Adapter (`adapter-spring-ai`)

## 1. Module Identity

| Field | Value |
| :--- | :--- |
| **Module name** | `adapter-spring-ai` |
| **Repository location** | `project/apex-platform/adapter-spring-ai/` |
| **Parent aggregator** | `project/apex-platform/pom.xml` |
| **Tier** | **Tier 2** (platform adapter — Spring AI allowed here, never in Tier 0) |
| **Governed by** | ADR-004 (Model-provider adapter seam and determinism boundary) |
| **Spike input** | `docs/spikes/A4-spring-ai-adapter.md` |
| **Decision input** | `docs/contracts/decisions/Q11-model-provider-seam.md` |
| **Spec readiness** | **READY** — Q11 closed, ADR-004 accepted, port classification unchanged |

## 2. Purpose

Integrate Spring AI (`ChatClient`, `ToolCallingAdvisor`) as a model-provider
adapter behind the existing Tier 0 `ChatModelPort` and `ToolExecutorPort`
seams. Spring AI types are confined to the Tier 2 adapter; Tier 0 sees only
plain Java value objects (`ChatRequest`, `ChatResponse`, `ToolCall`,
`ToolResult`). The adapter is the explicit boundary where determinism may be
relaxed — the core planner remains deterministic; model output is not assumed
deterministic.

## 3. Ports Consumed and Provided

### Consumed (Tier 0 contracts)

| Port | Package | Used as |
|------|---------|---------|
| `ChatModelPort` | `com.apex.engine.core.port` | Implemented by translating Spring AI `ChatClient` calls |
| `ToolExecutorPort` | `com.apex.engine.core.port` | Implemented by mapping `ToolCallingAdvisor` events |

### Provided (Tier 2 implementations)

| Implementation | Port | Spring AI component |
|---------------|------|---------------------|
| `SpringAiChatModel` | `ChatModelPort` | `ChatClient` (OpenAI-compatible default) |
| `SpringAiToolExecutor` | `ToolExecutorPort` | `ToolCallingAdvisor` (tool loop owner) |

### Port classification

- **`ChatModelPort`**: **unchanged** — no method signature changes; the
  adapter implements the existing interface.
- **`ToolExecutorPort`**: **unchanged** — no method signature changes; the
  adapter implements the existing interface.
- **No new Tier 0 ports introduced.**

## 4. Public API Surface and Compatibility

### Java API

The adapter exposes two implementation classes:

```java
public class SpringAiChatModel implements ChatModelPort {
    public SpringAiChatModel(ChatClient chatClient);
    public ChatResponse chat(ChatRequest request);
    public void stream(ChatRequest request, CancellationToken token,
                        Consumer<ChatChunk> onChunk);
}

public class SpringAiToolExecutor implements ToolExecutorPort {
    public SpringAiToolExecutor(ToolCallingAdvisor advisor,
                               ToolExecutorPort delegate);
    public ToolResult execute(ToolCall call);
}
```

### Compatibility statement

- **`ChatModelPort.chat(ChatRequest)`**: unchanged — implemented by the
  adapter, not modified.
- **`ChatModelPort.stream(...)`**: unchanged — default implementation calls
  `chat()` once and emits a single terminal chunk; the adapter may override
  for real streaming.
- **`ToolExecutorPort.execute(ToolCall)`**: unchanged — implemented by the
  adapter, not modified.
- **Tier 0 port signatures**: unchanged — no Spring AI types appear in port
  signatures.

## 5. Dependency Ceiling and Justification

| Dependency | Scope | Justification |
| :--- | :--- | :--- |
| `org.springframework.ai:spring-ai-core` | Compile | Spring AI 2.0 `ChatClient`, `ToolCallingAdvisor` (Apache 2.0, within allowed license set). |
| `com.apex:engine-core` | Compile | Tier 0 ports and model records (project dependency). |
| `org.jspecify` | Compile | Null-safety annotations per project standards. |
| `org.junit.jupiter:junit-jupiter` | Test | Test framework per project standard. |
| `org.assertj:assertj-core` | Test | Assertions per project standard. |
| `com.tngtech.archunit:archunit-junit5` | Test | Architectural enforcement per project standard. |

**Spring AI version/provider:** OpenAI-compatible provider as the default
(Q9 resolved). Provider selection is starter configuration, not a Tier 0
concern.

**Tier 0 purity:** Zero Spring AI types in `engine-core`, `engine-planner`,
`engine-tools`, `engine-store`, `engine-json`, or `engine-memory`. The
adapter depends on Tier 0; Tier 0 never depends on the adapter.
`CorePurityTest` must pass for all Tier 0 modules.

## 6. Implementation Design

### 6.1 Adapter seam

The adapter implements `ChatModelPort` by translating Spring AI `ChatClient`
calls into `ChatRequest`/`ChatResponse` value objects. `ChatModelPort.stream()`
overrides the default to stream real deltas from `ChatClient`, checking the
`CancellationToken` between chunks.

### 6.2 Tool-calling integration

Spring AI 2.0's `ToolCallingAdvisor` owns the tool loop inside the
`ChatClient`'s advisor chain. The adapter:
- Does **not** implement a parallel tool loop.
- Maps `ToolCallingAdvisor` tool-call events onto `ToolExecutorPort.execute(ToolCall)`.
- Maps tool-result events back into `ChatResponse` chunks.
- Exposes tools registered through `ChatClient.builder().defaultTools(...)`
  to the tool loop; does not silently bypass the builder's tool set.

### 6.3 Determinism boundary

The adapter is the explicit boundary where determinism may be relaxed:
- The core planner remains deterministic given the same inputs.
- Model output is nondeterministic by nature but is received by Tier 0 as
  an opaque `ChatResponse`.
- Tool execution side-effects are nondeterministic but confined to the
  adapter; results are fed back into `PlanLoop` as opaque `ToolResult`.
- The adapter **does not claim to preserve determinism** — this is stated
  explicitly to prevent treating a non-deterministic model response as a
  determinism regression in the core.

### 6.4 Failure and cancellation mapping

- Spring AI exceptions → mapped to `ToolResult.failure(...)` or
  `ChatResponse` error — never thrown across the port boundary.
- `CancellationToken` checked between stream chunks and before each tool
  execution; cancelled calls return a cancelled result, not a framework
  exception.
- Timeouts delegated to the Spring AI client configuration; on timeout,
  return a failure result.

## 7. Determinism Impact

**Determinism is relaxed at the adapter boundary only.** The core planner
(`PlanLoop` + `AStarPlanner`) remains fully deterministic. Model output and
tool side-effects are nondeterministic but confined to Tier 2. `PlanLoop`
remains deterministic given the same `ToolResult` inputs.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **Model provider** | Provider choice (OpenAI-compatible, Azure, Ollama) is adapter |
| | configuration; no provider-specific types in Tier 0. |
| **API keys** | Provided via `SecretsPort` bean; secret storage is the |
| | deployment's responsibility. |
| **Tool execution** | Tool results are opaque to Tier 0; sandboxing is handled by |
| | the tool sandboxing spec (Q15/Q16). |

## 9. Audit Impact

Every model call and tool invocation through the adapter is a candidate for
audit logging. The adapter does not write to the audit chain directly —
audit events flow through `AuditPort` (bean). Tool invocations and their
results are recorded by whatever `AuditPort` implementation is active.

## 10. Failure, Cancellation, and Timeout Semantics

| Scenario | Required Behavior |
| :--- | :--- |
| **Model provider error** | Mapped to `ChatResponse` error; never thrown |
| | across the port boundary. |
| **Tool execution error** | Mapped to `ToolResult.failure(...)`; never thrown |
| | across the port boundary. |
| **Cancellation** | `CancellationToken` checked between stream chunks |
| | and before each tool execution; cancelled calls return a cancelled |
| | result. |
| **Timeout** | Delegated to Spring AI client config; on timeout, return a |
| | failure result. |

## 11. Test Plan

### 11.1 Unit tests

- `SpringAiChatModel.chat()` translates `ChatRequest` to Spring AI
  `ChatClient` call and back to `ChatResponse`.
- `SpringAiChatModel.stream()` checks `CancellationToken` between chunks.
- `SpringAiToolExecutor.execute()` translates `ToolCall` to `ToolCallingAdvisor`
  invocation and back to `ToolResult`.
- Spring AI exceptions mapped to `ChatResponse` error / `ToolResult.failure`,
  never thrown across port boundary.
- No Spring AI types in any Tier 0 class (verified by `CorePurityTest`).

### 11.2 Integration tests

- **Tool-calling round trip:** A tool-calling request through the advisor
  results in exactly one core `ToolExecutorPort.execute(ToolCall)` per model
  tool call (acceptance test minimum).
- **Cancelled call:** A cancelled `CancellationToken` surfaces as a core
  cancellation, not a framework exception (acceptance test minimum).
- **Tool registration:** Tools registered via `defaultTools(...)` are exposed
  to the tool loop; the adapter does not bypass the builder's tool set.
- **Error mapping:** Spring AI errors surface as `ChatResponse` errors or
  `ToolResult.failure(...)`, not as uncaught exceptions.

### 11.3 Architectural enforcement

- `CorePurityTest` passes in all Tier 0 modules.
- No Spring AI types (`ChatClient`, `ToolCallback`, `ToolCallingAdvisor`,
  etc.) in any Tier 0 class.

### 11.4 Coverage targets

- `ChatModelPort.chat()` and `stream()` exercised.
- `ToolExecutorPort.execute()` exercised.
- Failure mapping paths exercised.
- Cancellation paths exercised.
- Tool registration paths exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. `adapter-spring-ai` builds under `mvn -B -f project/apex/pom.xml verify`.
2. `ChatModelPort` and `ToolExecutorPort` signatures unchanged.
3. `SpringAiChatModel` implements `ChatModelPort`; `SpringAiToolExecutor`
   implements `ToolExecutorPort`.
4. Tool-calling round trip through `ToolCallingAdvisor` results in exactly
   one core `ToolExecutorPort.execute(ToolCall)` per model tool call
   (acceptance test minimum).
5. Cancelled call surfaces as core cancellation, not framework exception
   (acceptance test minimum).
6. `ToolCallingAdvisor` owns the tool loop; no parallel tool loop in the
   adapter.
7. Tools registered via `defaultTools(...)` exposed to the tool loop.
8. Spring AI exceptions mapped to `ChatResponse` error /
   `ToolResult.failure(...)`, never thrown across port boundary.
9. `CancellationToken` checked between stream chunks and before tool
   execution.
10. `CorePurityTest` passes in all Tier 0 modules (no Spring AI types in
    Tier 0).
11. Spring AI (Apache 2.0) within allowed license set.

## 13. Rollback / Disable Strategy

- **Exclude the adapter:** Applications that do not want Spring AI simply
  do not add `adapter-spring-ai` as a dependency.
- **Alternative provider:** Any other `ChatModelPort` implementation can
  replace `SpringAiChatModel` without changing Tier 0.
- **No Tier 0 impact:** Removing the adapter has zero effect on Tier 0
  modules.

## 14. Out of Scope (Explicit)

- Streaming beyond v1 (may be deferred; `ChatModelPort.stream()` default
  emits a single terminal chunk).
- Tool sandboxing (handled by the tool sandboxing spec, Q15/Q16).
- MCP adapter (separate spec, depends on sandboxing).
- Provider-specific features beyond OpenAI-compatible chat.

## 15. Governance References

| Artifact | Path |
| :--- | :--- |
| Spike finding | `docs/spikes/A4-spring-ai-adapter.md` |
| Governing ADR | `docs/adr/ADR-004-model-provider-adapter-seam.md` |
| Decision note | `docs/contracts/decisions/Q11-model-provider-seam.md` |
| Port contract inventory | `docs/contracts/ports.md` (`ChatModelPort`, `ToolExecutorPort` rows) |
| NFR capture | `docs/contracts/nfr.md` §4 |
| Open questions | `docs/contracts/open-questions.md` (Q11 closed) |
| Spec readiness checklist | `docs/specs/README.md` |

**Decision governance:** Q11 (adapter seam behind Tier 0 model-provider port,
`ToolCallingAdvisor` owns tool loop, explicit determinism-boundary statement,
Spring AI Apache 2.0 license) is closed and linked above. These decisions
constrain this spec as stated in each decision note's "Constraint on future
specs" section.
