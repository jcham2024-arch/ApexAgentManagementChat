# ADR-005: External tool protocol adapter contract

**Status:** Accepted  
**Deciders:** Engineering team  
**Date:** 2026-11-10  
**Supersedes:** none  
**Depends on:** Spike A5 (docs/spikes/A5-mcp-adapter.md)

## Context

The Model Context Protocol (MCP) is a tool-server protocol that lets an LLM client discover and invoke remote tools. MCP must map onto the existing Tier 0 `ToolExecutorPort` without coupling Tier 0 to MCP-specific types or transport concepts.

## Decision

**Adopt a Tier 2 MCP tool-server adapter implementing `ToolExecutorPort` (A5).**

- MCP `tools/list` → advertise tool metadata (name, description, input schema) through `ToolExecutorPort`-compatible discovery.
- MCP `tools/call` → `ToolExecutorPort.execute(ToolCall)`; the adapter translates MCP JSON-RPC params into `ToolCall` and the server result into `ToolResult`.
- Transport: HTTP + Streamable JSON-RPC (MCP 2024-11+) as primary, stdio supported for local subprocess tools.
- Tool discovery: poll `tools/list` at startup and on refresh; build name → input-schema map.
- Per-call timeout (default 30s); on timeout return `ToolResult.failure(timeout)`.
- Cancellation: if `CancellationToken` is cancelled, abort the JSON-RPC request (client-initiated cancel) and return a cancelled result.
- MCP/transport/tool-server errors map to `ToolResult.failure(...)` — Tier 0 sees only `ToolResult`.
- No MCP types leak into Tier 0.

## Alternatives considered

| Option | Verdict | Reason |
|--------|---------|--------|
| MCP adapter on `ToolExecutorPort` (A5) | **Accepted** | Reuses existing Tier 0 port; MCP concerns confined to adapter. |
| New MCP-specific Tier 0 port | Rejected | Couples Tier 0 to MCP transport/discovery concepts. |

## Consequences

### Positive
- MCP tool servers usable through the same `ToolExecutorPort` seam as local tools.
- Tier 0 sees only `ToolCall`/`ToolResult`; protocol details are adapter-level.
- Clean timeout/cancellation/error mapping.

### Negative
- MCP SDK/library choice and license compatibility (Apache/MIT/BSD/ISC per AGENT_RULES) is unresolved (UNSET — requires stakeholder input).
- stdio vs HTTP transport default deferred.

## Related

- docs/spikes/A5-mcp-adapter.md — the spike finding behind this decision
- `ToolExecutorPort`, `ToolCall`, `ToolResult` in `engine-core/port` / `port/model` (Tier 0)