# Spike A7 — Tooling

**Status:** Finding  
**Date:** 2026-11-10  
**Outcome:** Promote to spec (tool port + v1 tool set, sandboxed)  
**Owner:** Engineering team

## Question

What belongs in `engine-tools`, and what are the sandboxing rules? (`engine-tools` is currently package-only: `ToolExecutorPort` lives in `engine-core/port`; `engine-tools` has no implementations or tests.)

## What belongs in `engine-tools` (Tier 0)

`engine-tools` is Tier 0 and must remain pure (`java.base` + `jspecify` only, per AGENT_RULES §2/§3). It owns:
- Tool metadata records: `ToolCall`, `ToolResult`, `ToolId` (already in `engine-core/port/model`).
- The `ToolExecutorPort` interface (already in `engine-core/port`) — note: this port arguably belongs in `engine-core/port`, not `engine-tools`; `engine-tools` can host tool *metadata* and *policy* records.
- `ToolPolicy` / risk-tier definitions (already in `engine-core/port/model` as `RiskLevel`, `GuardrailSet`).

`engine-tools` does **not** own tool implementations (file I/O, HTTP, process execution) — those are I/O and belong in Tier 1 adapters (`engine-tools-impl`).

## v1 tool scope (Tier 1 implementations, `engine-tools-impl`)

| Tool | Tier 1 impl | Sandbox restriction | Deterministic? |
|------|-------------|---------------------|----------------|
| `read` | `engine-tools-impl` | Read-only paths, path-traversal blocked | Yes (given same FS state) |
| `write` | `engine-tools-impl` | Write-only paths, no exec bits, size cap | No (mutates FS) |
| `list` | `engine-tools-impl` | Directory listing only, depth cap | Yes |
| `http` | `engine-tools-impl` | Allow-listed hosts, timeout, no local/private IPs | No (network) |
| `run` | `engine-tools-impl` | Command allow-list, no shell interpolation, timeout | No (process) |

## Sandboxing rules

1. **No direct Tier 0 I/O.** `engine-tools` (Tier 0) declares `ToolExecutorPort` and metadata only; it never calls `java.io`/`java.nio.file`/`java.net`/`ProcessBuilder`.
2. **Path confinement.** File tools resolve against a declared root directory; reject `..` traversal and absolute paths outside root.
3. **Network allow-list.** `http` tool only contacts allow-listed hosts; blocks private/link-local addresses (SSRF prevention).
4. **Command allow-list.** `run` tool executes only pre-approved commands/args; no shell interpolation (`/bin/sh -c` with user input).
5. **Timeouts on every tool.** Every tool execution bounds wall-clock time; on timeout, return `ToolResult.failure(timeout)`.
6. **Cancellation.** Tool execution checks `CancellationToken` before starting and at checkpoints; if cancelled, returns cancelled result without side-effects where possible.
7. **Audit every tool call.** Every `execute(...)` is recorded via `AuditPort` (tool name, args redacted, outcome, duration) so tool use is auditable.

## Determinism

Tools that perform I/O/HTTP/process execution are inherently nondeterministic. This is acceptable because the nondeterminism is confined to the Tier 1 adapter (`engine-tools-impl`); Tier 0's `PlanLoop` remains deterministic given the same `ToolResult` inputs. Tool results are fed back into `PlanLoop` as opaque values.

## Recommendation

**Promote A7 (tool port + v1 tool set in `engine-tools-impl`, sandboxed per the rules above)**.

Rationale:
- Keeps Tier 0 pure; tool implementations (I/O) live in Tier 1.
- Clear v1 tool scope with sandbox rules.
- Every tool call is audited and cancellation-safe.
- Rejected alternative (putting tool implementations in Tier 0): violates Tier 0 purity (I/O prohibited).

## Open questions

- Exact allow-list of hosts/commands for v1 — **UNSET — requires stakeholder input**.
- Tool result size caps and duration caps — **UNSET — requires stakeholder input**.

## Related

- ADR-007: Tool sandboxing and capability restrictions (to be written, Phase B)
- `ToolExecutorPort`, `ToolCall`, `ToolResult`, `ToolId` in `engine-core/port` / `port/model` (Tier 0)
- `engine-tools-impl` (Tier 1) — target for v1 tool implementations
