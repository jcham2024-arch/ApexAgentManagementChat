# BUILD-SPEC: Spring AI Model Provider Adapter (`adapter-spring-ai`) — Part 2 of 2

**Source:** docs/specs/spring-ai-adapter-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 2 of 2
**Cross-reference:** See `spring-ai-adapter-spec-part-1.md` for sections 1-6.

## 7. Determinism Impact

**Determinism is relaxed at the adapter boundary only.** The core planner
(`PlanLoop` + `AStarPlanner`) remains fully deterministic. Model output
and tool side-effects are nondeterministic but confined to Tier 2.
`PlanLoop` remains deterministic given the same `ToolResult` inputs.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **Model provider** | Provider choice (OpenAI-compatible, Azure, |
| | Ollama) is adapter configuration; no provider-specific types in |
| | Tier 0. |
| **API keys** | Provided via `SecretsPort` bean; secret storage is the |
| | deployment's responsibility. |
| **Tool execution** | Tool results are opaque to Tier 0; sandboxing is |
| | handled by the tool sandboxing spec (Q15/Q16). |

## 9. Audit Impact

Every model call and tool invocation through the adapter is a candidate
for audit logging. The adapter does not write to the audit chain
directly — audit events flow through `AuditPort` (bean). Tool
invocations and their results are recorded by whatever `AuditPort`
implementation is active.

## 10. Failure, Cancellation, and Timeout Semantics

| Scenario | Required Behavior |
| :--- | :--- |
| **Model provider error** | Mapped to `ChatResponse` error; never |
| | thrown across the port boundary. |
| **Tool execution error** | Mapped to |
| `ToolResult.failure(...)`; never thrown across the port boundary. |
| **Cancellation** | `CancellationToken` checked between stream |
| | chunks and before each tool execution; cancelled calls return a |
| | cancelled result, not a framework exception. |
| **Timeout** | Delegated to Spring AI client configuration; on |
| | timeout, return a failure result. |

## 11. Test Plan

### 11.1 Unit tests

- `SpringAiChatModel.chat()` translates `ChatRequest` to Spring AI |
| `ChatClient` call and back to `ChatResponse`.
- `SpringAiChatModel.stream()` checks `CancellationToken` between |
| chunks.
- `SpringAiToolExecutor.execute()` translates `ToolCall` to |
| `ToolCallingAdvisor` invocation and back to `ToolResult`.
- Spring AI exceptions mapped to `ChatResponse` error / |
| `ToolResult.failure`, never thrown across port boundary.
- No Spring AI types in any Tier 0 class (verified by |
| `CorePurityTest`).

### 11.2 Integration tests

- **Tool-calling round trip:** A tool-calling request through the |
| advisor results in exactly one core `ToolExecutorPort.execute(ToolCall)`
| per model tool call (acceptance test minimum).
- **Cancelled call:** A cancelled `CancellationToken` surfaces as a |
| core cancellation, not a framework exception (acceptance test |
| minimum).
- **Tool registration:** Tools registered via `defaultTools(...)` |
| are exposed to the tool loop; the adapter does not bypass the |
| builder's tool set.
- **Error mapping:** Spring AI errors surface as `ChatResponse` |
| errors or `ToolResult.failure(...)`, not as uncaught exceptions.

### 11.3 Architectural enforcement

- `CorePurityTest` passes in all Tier 0 modules.
- No Spring AI types (`ChatClient`, `ToolCallback`, |
| `ToolCallingAdvisor`, etc.) in any Tier 0 class.

### 11.4 Coverage targets

- `ChatModelPort.chat()` and `stream()` exercised.
- `ToolExecutorPort.execute()` exercised.
- Failure mapping paths exercised.
- Cancellation paths exercised.
- Tool registration paths exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. `adapter-spring-ai` builds under `mvn -B -f |
| project/apex/pom.xml verify`.
2. `ChatModelPort` and `ToolExecutorPort` signatures unchanged.
3. `SpringAiChatModel` implements `ChatModelPort`; |
| `SpringAiToolExecutor` implements `ToolExecutorPort`.
4. Tool-calling round trip through `ToolCallingAdvisor` results in |
| exactly one core `ToolExecutorPort.execute(ToolCall)` per model tool |
| call (acceptance test minimum).
5. Cancelled call surfaces as core cancellation, not framework |
| exception (acceptance test minimum).
6. `ToolCallingAdvisor` owns the tool loop; no parallel tool loop |
| in the adapter.
7. Tools registered via `defaultTools(...)` exposed to the tool |
| loop.
8. Spring AI exceptions mapped to `ChatResponse` error /
   `ToolResult.failure(...)`, never thrown across port boundary.
9. `CancellationToken` checked between stream chunks and before |
| tool execution.
10. `CorePurityTest` passes in all Tier 0 modules (no Spring AI |
| types in Tier 0).
11. Spring AI (Apache 2.0) within allowed license set.

## 13. Rollback / Disable Strategy

- **Exclude the adapter:** Applications that do not want Spring AI |
| simply do not add `adapter-spring-ai` as a dependency.
- **Alternative provider:** Any other `ChatModelPort` |
| implementation can replace `SpringAiChatModel` without changing |
| Tier 0.
- **No Tier 0 impact:** Removing the adapter has zero effect on |
| Tier 0 modules.

## 14. Out of Scope (Explicit)

- Streaming beyond v1 (may be deferred; `ChatModelPort.stream()` |
| default emits a single terminal chunk).
- Tool sandboxing (handled by the tool sandboxing spec, Q15/Q16).
- MCP adapter (separate spec, depends on sandboxing).
- Provider-specific features beyond OpenAI-compatible chat.

## 15. Governance References

| Artifact | Path |
| :--- | :--- |
| Spike finding | `docs/spikes/A4-spring-ai-adapter.md` |
| Governing ADR | `docs/adr/ADR-004-model-provider-adapter-seam.md` |
| Decision note | `docs/contracts/decisions/Q11-model-provider-seam.md` |
| Port contract inventory | `docs/contracts/ports.md` (`ChatModelPort`, |
| | `ToolExecutorPort` rows) |
| NFR capture | `docs/contracts/nfr.md` §4 |
| Open questions | `docs/contracts/open-questions.md` (Q11 |
| | closed) |
| Spec readiness checklist | `docs/specs/README.md` |

**Decision governance:** Q11 (adapter seam behind Tier 0 |
| model-provider port, `ToolCallingAdvisor` owns tool loop, explicit |
| determinism-boundary statement, Spring AI Apache 2.0 license) is |
| closed and linked above. These decisions constrain this spec as |
| stated in each decision note's "Constraint on future specs" |
| section.
