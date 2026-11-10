# ADR-004: Model-provider adapter seam and determinism boundary

**Status:** Accepted  
**Deciders:** Engineering team  
**Date:** 2026-11-10  
**Supersedes:** none  
**Depends on:** Spike A4 (docs/spikes/A4-spring-ai-adapter.md)

## Context

Spring AI (`ChatClient`, `ToolCallback`) must be integrated as a model-provider adapter. Tier 0 must remain deterministic and free of provider-specific types. The question is where the seam lies and how nondeterminism (model output, tool side-effects) is contained at the adapter boundary.

## Decision

**Adopt `ChatModelPort` as the model-inference seam and `ToolExecutorPort` as the tool-execution seam; both implemented by a Tier 2 `adapter-spring-ai` module.**

- `ChatModelPort.chat(ChatRequest)` and `stream(...)` are implemented by translating Spring AI `ChatClient` calls into `ChatRequest`/`ChatResponse` value objects.
- Spring AI `ToolCallback`/`@Tool` methods map to `ToolExecutorPort.execute(ToolCall)`.
- Model output is nondeterministic by nature but is received by Tier 0 as an opaque `ChatResponse`; `PlanLoop` remains deterministic given the same inputs.
- Tool execution side-effects (I/O/HTTP/process) are nondeterministic but confined to Tier 1/Tier 2 adapters; results are fed back into `PlanLoop` as opaque `ToolResult`.
- Spring AI exceptions map to `ToolResult.failure(...)` / `ChatResponse` error — never thrown across the port boundary.
- `CancellationToken` is checked between stream chunks and before each tool execution; cancelled calls return a cancelled result.
- No Spring AI types (`ChatClient`, `ToolCallback`, etc.) appear in Tier 0.

## Alternatives considered

| Option | Verdict | Reason |
|--------|---------|--------|
| Existing `ChatModelPort` + `ToolExecutorPort` seam (A4) | **Accepted** | Reuses existing Tier 0 ports; no new Tier 0 API; nondeterminism confined to adapter. |
| New Spring-AI-specific Tier 0 port | Rejected | Couples Tier 0 to a provider abstraction, violating port/adapter separation. |

## Consequences

### Positive
- Determinism is a Tier 0 invariant; the adapter boundary is where the nondeterministic world enters.
- Provider-agnostic core: Spring AI is one of many possible `ChatModelPort` implementations.
- Clean failure/cancellation mapping.

### Negative
- Spring AI version/provider (OpenAI-compatible vs Azure vs Ollama) is deployment-specific (UNSET — requires stakeholder input).
- Streaming may be deferred beyond v1 (defer to ADR-004 review).

## Related

- docs/spikes/A4-spring-ai-adapter.md — the spike finding behind this decision
- `ChatModelPort`, `ToolExecutorPort` in `engine-core/port` (Tier 0)
