# Spike A5 — MCP Adapter

**Status:** Finding  
**Date:** 2026-11-10  
**Outcome:** Promote to spec (adapter boundary only)  
**Owner:** Engineering team

## Question

What is the shape of an MCP tool-server adapter?

## Model Context Protocol (MCP) concepts

MCP is a client-server protocol where a host exposes tools to an LLM client. Relevant concepts:
- **Transport**: stdio, HTTP+SSE, or HTTP+Streamable JSON-RPC.
- **Tool discovery**: `tools/list` — server advertises available tools with input schemas.
- **Tool invocation**: `tools/call` — client invokes a tool by name with arguments; server returns a result.
- **Timeouts/cancellation**: client-initiated request timeouts; cancellation via JSON-RPC lifecycle.

## Mapping onto Tier 0 ports

MCP tools map onto `ToolExecutorPort` (Tier 0):
- MCP `tools/list` → advertise `ToolCall`-compatible tool metadata (name, description, input schema).
- MCP `tools/call` → `ToolExecutorPort.execute(ToolCall)`; the adapter translates MCP JSON-RPC params into `ToolCall` and the result into `ToolResult`.

MCP is a *tool-server* protocol, not a model-inference protocol, so it does not map onto `ChatModelPort`. It complements (does not replace) the model provider adapter.

## Required answers

### Transport assumptions

Recommended: **HTTP + Streamable JSON-RPC** (MCP 2024-11+) as the primary transport for server-to-server integration, with stdio supported for local subprocess tools. The adapter opens a persistent MCP client connection to the tool server and reuses it across calls.

### Tool-discovery model

The adapter polls `tools/list` at startup and on refresh, building a map of tool name → input schema. Each discovered tool is exposed through `ToolExecutorPort` by name. Dynamic discovery (re-poll on unknown tool) is optional for v1.

### Timeout/cancellation semantics

- Per-call timeout configurable (default 30s); on timeout, return `ToolResult.failure(timeout)`.
- Cancellation: if the `CancellationToken` is cancelled before/during an MCP call, the adapter aborts the JSON-RPC request (client-initiated cancel) and returns a cancelled result.
- Tool server errors (JSON-RPC errors) → `ToolResult.failure(error)`.

### How remote tool errors map to core error reporting

MCP errors are opaque to Tier 0. The adapter maps any MCP/transport/tool-server error into `ToolResult.failure(...)` with a message; Tier 0's `PlanLoop` treats it as a failed step (existing failure path). No MCP types leak into Tier 0.

## Recommendation

**Promote A5 (MCP tool-server adapter implementing `ToolExecutorPort` as Tier 2)**.

Rationale:
- Reuses the existing `ToolExecutorPort` seam — no new Tier 0 port required.
- MCP concerns (transport, discovery, timeouts) are adapter-level; Tier 0 sees only `ToolCall`/`ToolResult`.
- Failure/cancellation mapping is clean and deterministic-safe.
- Rejected alternative (MCP as a new Tier 0 port): would couple Tier 0 to MCP-specific types and transport concepts, violating port/adapter separation.

## Open questions

- MCP SDK/library choice and its license compatibility (must be Apache/MIT/BSD/ISC per AGENT_RULES) — **UNSET — requires stakeholder input**.
- stdio vs HTTP transport default — defer to ADR-005.

## Related

- ADR-005: External tool protocol adapter contract (to be written, Phase B)
- `ToolExecutorPort` in `engine-core/port` (Tier 0)
