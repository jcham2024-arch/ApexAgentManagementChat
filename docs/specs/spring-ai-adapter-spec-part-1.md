# BUILD-SPEC: Spring AI Model Provider Adapter (`adapter-spring-ai`) — Part 1 of 2

**Source:** docs/specs/spring-ai-adapter-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 1 of 2
**Cross-reference:** See `spring-ai-adapter-spec-part-2.md` for sections 7-15.

## 1. Module Identity

| Field | Value |
| :--- | :--- |
| **Module name** | `adapter-spring-ai` |
| **Repository location** | `project/apex-platform/adapter-spring-ai/` |
| **Parent aggregator** | `project/apex-platform/pom.xml` |
| **Tier** | **Tier 2** (platform adapter — Spring AI allowed here, never in |
| | Tier 0) |
| **Governed by** | ADR-004 (Model-provider adapter seam and determinism boundary) |
| **Spike input** | `docs/spikes/A4-spring-ai-adapter.md` |
| **Decision input** | `docs/contracts/decisions/Q11-model-provider-seam.md` |
| **Spec readiness** | **READY** — Q11 closed, ADR-004 accepted, port classification |
| | unchanged |

## 2. Purpose

Integrate Spring AI (`ChatClient`, `ToolCallingAdvisor`) as a
model-provider adapter behind the existing Tier 0 `ChatModelPort` and
`ToolExecutorPort` seams. Spring AI types are confined to the Tier 2
adapter; Tier 0 sees only plain Java value objects
(`ChatRequest`, `ChatResponse`, `ToolCall`, `ToolResult`). The adapter
is the explicit boundary where determinism may be relaxed — the core
planner remains deterministic; model output is not assumed deterministic.

## 3. Ports Consumed and Provided

### Consumed (Tier 0 contracts)

| Port | Package | Used as |
|------|---------|---------|
| `ChatModelPort` | `com.apex.engine.core.port` | Implemented by translating Spring |
| | | AI `ChatClient` calls |
| `ToolExecutorPort` | `com.apex.engine.core.port` | Implemented by mapping |
| | | `ToolCallingAdvisor` events |

### Provided (Tier 2 implementations)

| Implementation | Port | Spring AI component |
|---------------|------|---------------------|
| `SpringAiChatModel` | `ChatModelPort` | `ChatClient` (OpenAI-compatible |
| | | default) |
| `SpringAiToolExecutor` | `ToolExecutorPort` | `ToolCallingAdvisor` |

### Port classification

- **`ChatModelPort`**: **unchanged** — no method signature changes; |
| the adapter implements the existing interface.
- **`ToolExecutorPort`**: **unchanged** — no method signature |
| changes; the adapter implements the existing interface.
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

- **`ChatModelPort.chat(ChatRequest)`**: unchanged — implemented by the |
| adapter, not modified.
- **`ChatModelPort.stream(...)`**: unchanged — default implementation |
| calls `chat()` once and emits a single terminal chunk; the adapter |
| may override for real streaming.
- **`ToolExecutorPort.execute(ToolCall)`**: unchanged — implemented by |
| the adapter, not modified.
- **Tier 0 port signatures**: unchanged — no Spring AI types appear in |
| port signatures.

## 5. Dependency Ceiling and Justification

| Dependency | Scope | Justification |
| :--- | :--- | :--- |
| `org.springframework.ai:spring-ai-core` | Compile | Spring AI 2.0 |
| | | `ChatClient`, `ToolCallingAdvisor` (Apache 2.0, within allowed |
| | | license set). |
| `com.apex:engine-core` | Compile | Tier 0 ports and model records |
| | | (project dependency). |
| `org.jspecify` | Compile | Null-safety annotations per |
| | | project standards. |
| `org.junit.jupiter:junit-jupiter` | Test | Test framework per project |
| | | standard. |
| `org.assertj:assertj-core` | Test | Assertions per project |
| | | standard. |
| `com.tngtech.archunit:archunit-junit5` | Test | Architectural |
| | | enforcement per project standard. |

**Spring AI version/provider:** OpenAI-compatible provider as the default
(Q9 resolved). Provider selection is starter configuration, not a Tier 0
concern.

**Tier 0 purity:** Zero Spring AI types in `engine-core`,
`engine-planner`, `engine-tools`, `engine-store`, `engine-json`, or
`engine-memory`. The adapter depends on Tier 0; Tier 0 never depends on
the adapter. `CorePurityTest` must pass for all Tier 0 modules.

## 6. Implementation Design

### 6.1 Adapter seam

The adapter implements `ChatModelPort` by translating Spring AI
`ChatClient` calls into `ChatRequest`/`ChatResponse` value objects.
`ChatModelPort.stream()` overrides the default to stream real deltas
from `ChatClient`, checking the `CancellationToken` between chunks.

### 6.2 Tool-calling integration

Spring AI 2.0's `ToolCallingAdvisor` owns the tool loop inside the
`ChatClient`'s advisor chain. The adapter:
- Does **not** implement a parallel tool loop.
- Maps `ToolCallingAdvisor` tool-call events onto |
| `ToolExecutorPort.execute(ToolCall)`.
- Maps tool-result events back into `ChatResponse` chunks.
- Exposes tools registered through |
| `ChatClient.builder().defaultTools(...)` to the tool loop; does not |
| silently bypass the builder's tool set.

### 6.3 Determinism boundary

The adapter is the explicit boundary where determinism may be relaxed:
- The core planner remains deterministic given the same inputs.
- Model output is nondeterministic by nature but is received by Tier 0 |
| as an opaque `ChatResponse`.
- Tool execution side-effects are nondeterministic but confined to the |
| adapter; results are fed back into `PlanLoop` as opaque `ToolResult`.
- The adapter **does not claim to preserve determinism** — this is |
| stated explicitly to prevent treating a non-deterministic model |
| response as a determinism regression in the core.

### 6.4 Failure and cancellation mapping

- Spring AI exceptions → mapped to `ToolResult.failure(...)` or |
| `ChatResponse` error — never thrown across the port boundary.
- `CancellationToken` checked between stream chunks and before each |
| tool execution; cancelled calls return a cancelled result, not a |
| framework exception.
- Timeouts delegated to the Spring AI client configuration; on |
| timeout, return a failure result.
