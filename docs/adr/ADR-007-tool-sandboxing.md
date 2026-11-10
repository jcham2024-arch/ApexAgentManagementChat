# ADR-007: Tool sandboxing and capability restrictions

**Status:** Accepted  
**Deciders:** Engineering team  
**Date:** 2026-11-10  
**Supersedes:** none  
**Depends on:** Spike A7 (docs/spikes/A7-tooling.md)

## Context

`engine-tools` (Tier 0) is currently package-only (`package-info.java` + `CorePurityTest`); `ToolExecutorPort` and tool metadata (`ToolCall`, `ToolResult`, `ToolId`) live in `engine-core/port` and `port/model`. There are no tool implementations and no sandboxing rules. Enterprise deployments need a bounded v1 tool set with strict sandboxing.

## Decision

**Adopt a pure Tier 0 `engine-tools` (port + metadata only) with v1 tool implementations in Tier 1 (`engine-tools-impl`), sandboxed per the rules below.**

- `engine-tools` (Tier 0) owns tool metadata records and policy/risk-tier records only. It never performs I/O (`java.io`/`java.nio.file`/`java.net`/`ProcessBuilder` prohibited per AGENT_RULES §2).
- v1 tool set (Tier 1, `engine-tools-impl`): `read`, `write`, `list`, `http`, `run`.
- Sandboxing rules:
  1. No Tier 0 I/O — implementations live in Tier 1 adapters.
  2. Path confinement: file tools resolve against a declared root; reject `..` traversal and absolute paths outside root.
  3. Network allow-list: `http` only contacts allow-listed hosts; blocks private/link-local addresses (SSRF prevention).
  4. Command allow-list: `run` executes only pre-approved commands/args; no shell interpolation.
  5. Timeouts on every tool; on timeout return `ToolResult.failure(timeout)`.
  6. Cancellation: check `CancellationToken` before start and at checkpoints; return cancelled result without side-effects where possible.
  7. Audit every tool call via `AuditPort` (tool name, args redacted, outcome, duration).
- Tools performing I/O/HTTP/process execution are nondeterministic but confined to Tier 1; Tier 0's `PlanLoop` remains deterministic given the same `ToolResult` inputs.

## Alternatives considered

| Option | Verdict | Reason |
|--------|---------|--------|
| Tier 0 port + Tier 1 implementations (A7) | **Accepted** | Keeps Tier 0 pure; clear v1 scope; sandbox rules enforced at adapter. |
| Tool implementations in Tier 0 | Rejected | Violates Tier 0 purity (I/O prohibited). |

## Consequences

### Positive
- Tier 0 purity preserved.
- Bounded, auditable, cancellation-safe v1 tool set.
- Tool use is recorded for compliance.

### Negative
- Exact host/command allow-lists and size/duration caps are deployment-specific (UNSET — requires stakeholder input).

## Related

- docs/spikes/A7-tooling.md — the spike finding behind this decision
- `ToolExecutorPort`, `ToolCall`, `ToolResult`, `ToolId` in `engine-core/port` / `port/model` (Tier 0)
- `engine-tools-impl` (Tier 1) — target for v1 tool implementations
