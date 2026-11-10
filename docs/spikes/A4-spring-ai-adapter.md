# Spike A4 — Spring AI Adapter

**Status:** Finding  
**Date:** 2026-11-10  
**Outcome:** Promote to spec (adapter boundary only)  
**Owner:** Engineering team

## Question

How do Spring AI `ChatClient` and tool-calling abstractions map onto the existing Tier 0 ports?

## Current seam

`ChatModelPort` (Tier 0, in `engine-core/port`) is the existing seam for model inference:
- `ChatResponse chat(ChatRequest request)`
- `default void stream(ChatRequest, CancellationToken, Consumer<ChatChunk>)`

There is also `ToolExecutorPort` for tool execution. Spring AI's `ChatClient` and `ToolCallback` abstractions are provider-specific and belong in a Tier 2 adapter, never in Tier 0.

## Mapping

| Spring AI concept | Maps to Tier 0 port | Determinism impact | Notes |
|-------------------|---------------------|--------------------|-------|
| `ChatClient.call(...)` / `ChatResponse` | `ChatModelPort.chat(ChatRequest)` | None (adapter translates) | Spring AI provider choice (OpenAI, Azure, etc.) is adapter config |
| Streaming `ChatClient.stream(...)` | `ChatModelPort.stream(...)` | None | Adapter checks `CancellationToken` between chunks |
| `ToolCallback` / `@Tool` methods | `ToolExecutorPort.execute(ToolCall)` | Tool side-effects are inherently nondeterministic — acceptable at adapter boundary | Determinism broken here, not in core |
| `Message` / `UserMessage` | `ChatRequest` model records | None | Pure value objects, jspecify-annotated |

## Required answers

### Which port(s) are the seam?

`ChatModelPort` is the primary seam; `ToolExecutorPort` is the secondary seam for tool-calling. The Spring AI adapter implements both as a Tier 2 module (`adapter-spring-ai`), translating Spring AI `ChatClient` calls into `ChatRequest`/`ChatResponse` and Spring AI tool callbacks into `ToolCall`/`ToolResult`.

### Where does determinism break, and is that acceptable at the adapter boundary?

Determinism breaks in two places, both confined to the Tier 2 adapter:
1. **Model output** — the LLM is nondeterministic by nature; the core receives the result as an opaque `ChatResponse`. The core's `PlanLoop` remains deterministic given the same inputs.
2. **Tool execution** — tools perform I/O/HTTP and are nondeterministic; results are fed back into `PlanLoop` as opaque `ToolResult`.

This is acceptable because determinism is a Tier 0 invariant; the adapter boundary is explicitly where the nondeterministic outside world enters.

### Failure and cancellation mapping

- Spring AI exceptions → mapped to `ToolResult.failure(...)` / `ChatResponse` error, not thrown across the port boundary.
- `CancellationToken` checked between stream chunks and before each tool execution; if cancelled, the adapter returns a cancelled result rather than invoking the model/tool.
- Timeouts delegated to the Spring AI client config; on timeout, return a failure result.

## Recommendation

**Promote A4 (Spring AI adapter implementing `ChatModelPort` + `ToolExecutorPort` as Tier 2)**.

Rationale:
- Reuses the existing `ChatModelPort` seam — no new Tier 0 port required.
- Determinism impact confined to the adapter boundary.
- Failure/cancellation mapping is well-defined and does not leak Spring AI types into Tier 0.
- Rejected alternative (new Spring-AI-specific port): would couple Tier 0 to a specific provider abstraction, violating the port/adapter separation.

## Open questions

- Spring AI version and provider (OpenAI-compatible vs Azure vs Ollama) — **UNSET — requires stakeholder input**.
- Whether streaming is required for v1 or can defer — defer to ADR-004.

## Related

- ADR-004: Model-provider adapter seam and determinism boundary (to be written, Phase B)
- `ChatModelPort`, `ToolExecutorPort` in `engine-core/port` (Tier 0)
