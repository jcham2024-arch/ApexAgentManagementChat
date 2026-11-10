# BUILD-SPEC: Tool Sandboxing and Capability Restrictions (`engine-tools-impl`) — Part 2-a of 3

**Source:** docs/specs/tool-sandboxing-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 2-a of 3
**Cross-reference:** See `tool-sandboxing-spec-part-2-b.md` for sections 11.3-15.
**Cross-reference:** See `tool-sandboxing-spec-part-1-a.md` and `tool-sandboxing-spec-part-1-b.md` for sections 1-6.

## 7. Determinism Impact

**No impact on Tier 0 determinism.** Tool execution side-effects (I/O,
HTTP, process) are nondeterministic but confined to Tier 1
(`engine-tools-impl`). `PlanLoop` remains deterministic given the same
`ToolResult` inputs — tool results are fed back as opaque values.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **Unsafe autonomy** | Prevented by deny-by-default; no full |
| | tool access "for flexibility". |
| **Unenforced manifest** | Forbidden — manifest compiled into |
| | runtime gate. |
| **Path traversal** | Blocked — file tools resolve |
| | against declared root; reject `..` |
| | and absolute paths outside root. |
| **SSRF** | Blocked — `http` tool only |
| | contacts allow-listed hosts; blocks |
| | private/link-local addresses. |
| **Shell injection** | Blocked — `run` tool uses command |
| | allow-list; no shell interpolation. |
| **Audit gap** | Closed — every invocation and |
| | denial logged via `AuditPort`. |

## 9. Audit Impact

Every tool invocation (tool name, args redacted, outcome, duration) and
every permission decision (allow or deny) is recorded via `AuditPort`.
The audit chain is tamper-evident — any post-hoc modification to an audit
entry breaks the chain.

## 10. Failure, Cancellation, and Timeout Semantics

| Scenario | Required Behavior |
| :--- | :--- |
| **Undeclared tool** | `GatewayDecision.Deny`; denial |
| | recorded in audit chain. |
| **Host outside allow-list** | `GatewayDecision.Deny`; denial |
| | recorded in audit chain. |
| **Sensitive action without confirmation** | Blocked; denial |
| | recorded in audit chain. |
| **Timeout** | `ToolResult.failure(timeout)`; no |
| | partial result visible. |
| **Cancellation** | `CancellationToken` checked |
| | before start and at checkpoints; |
| | cancelled result without side |
| | effects where possible. |
| **Tool exception** | Mapped to `ToolResult.failure(...)`; |
| | never thrown across port |
| | boundary. |

## 11. Test Plan

### 11.1 Unit tests

- `CapabilityManifest` with empty fields → nothing permitted.
- `ToolGate.authorize()` denies undeclared tool.
- `ToolGate.authorize()` allows declared tool.
- `ToolGate.authorize()` denies host outside allow-list.
- Deny-wins: explicit deny overrides allow.
- Path traversal: `read`/`write` with `..` rejected.
- SSRF: `http` to private/link-local address rejected.
- Shell injection: `run` with shell interpolation rejected.
- Timeout: `ToolResult.failure(timeout)` on tool timeout.
- Cancellation: `CancellationToken` checked before tool start.

### 11.2 Integration tests

- **Undeclared tool rejected:** A tool not in |
| the manifest is denied (acceptance test |
| minimum).
- **Declared tool allowed:** A tool in the |
| manifest is allowed (acceptance test |
| minimum).
- **Outbound host denied + audit:** An `http` |
| call to a host outside the allow-list is |
| denied, and the denial is recorded in |
| the audit chain (acceptance test |
| minimum).
- **Sensitive action blocked:** A sensitive |
| action without confirmation is blocked |
| (acceptance test minimum).
- **Every invocation audited:** Every |
| `execute()` call produces an `AuditEntry` |
| via `AuditPort`.
- **Every denial audited:** Every |
| `GatewayDecision.Deny` produces an |
| `AuditEntry` via `AuditPort`.
