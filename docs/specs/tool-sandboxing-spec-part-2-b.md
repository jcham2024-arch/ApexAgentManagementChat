# BUILD-SPEC: Tool Sandboxing and Capability Restrictions (`engine-tools-impl`) — Part 2-b of 3

**Source:** docs/specs/tool-sandboxing-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 2-b of 3
**Cross-reference:** See `tool-sandboxing-spec-part-2-a.md` for sections 7-11.2.
**Cross-reference:** See `tool-sandboxing-spec-part-1-a.md` and `tool-sandboxing-spec-part-1-b.md` for sections 1-6.

## 11.3 Architectural enforcement

- `CorePurityTest` passes in all Tier 0 modules.
- `engine-tools` (Tier 0) contains no I/O implementations.

## 11.4 Coverage targets

- All five tools (`read`, `write`, `list`, `http`, `run`) exercised.
- Manifest allow/deny logic exercised.
- Sandbox restrictions (path, network, shell) exercised.
- Audit recording (invocations + denials) exercised.
- Timeout and cancellation paths exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. `engine-tools-impl` builds under `mvn -B -f project/apex/pom.xml verify`.
2. Five kernel tools implemented: `read`, `write`, `list`, `http`, `run`.
3. Undeclared tool rejected by `ToolGate` (acceptance test minimum).
4. Declared tool allowed by `ToolGate` (acceptance test minimum).
5. Outbound host outside allow-list denied + denial recorded in audit |
| chain (acceptance test minimum).
6. Sensitive action without confirmation blocked (acceptance test |
| minimum).
7. Every tool invocation recorded via `AuditPort`.
8. Every permission decision (allow or deny) recorded via `AuditPort`.
9. Path traversal, SSRF, and shell injection blocked.
10. Every tool execution bounded by timeout.
11. `CancellationToken` checked before tool start and at checkpoints.
12. `CorePurityTest` passes in all Tier 0 modules.
13. Manifest is compiled into runtime gate (not just documentation).

## 13. Rollback / Disable Strategy

- **Exclude the module:** Applications that do not need tool |
| execution simply do not add `engine-tools-impl` as a dependency.
- **Permissive manifest:** A manifest with all tools/hosts/paths |
| allowed (opt-in, not default) for trusted environments.
- **No Tier 0 impact:** `engine-tools` (Tier 0) hosts metadata |
| only; removing `engine-tools-impl` has zero effect on Tier 0.

## 14. Out of Scope (Explicit)

- Exact host/command allow-lists for v1 (Q15/Q16 — populated per |
| deployment; empty by default).
- Tool result size caps and duration caps (Q17 non-blocking; |
| deferred).
- Sandbox escape detection beyond path/network/process |
| restrictions.
- Remote tool execution (MCP handled by separate spec).

## 15. Governance References

| Artifact | Path |
| :--- | :--- |
| Spike finding | `docs/spikes/A7-tooling.md` |
| Governing ADR | `docs/adr/ADR-007-tool-sandboxing.md` |
| Decision note | `docs/contracts/decisions/Q15-Q16-tool-sandboxing.md` |
| Port contract inventory | `docs/contracts/ports.md` |
| | (`ToolExecutorPort`, `ToolGatewayPort`, `ToolPolicy` rows) |
| NFR capture | `docs/contracts/nfr.md` §7 |
| Open questions | `docs/contracts/open-questions.md` (Q15, Q16 |
| | closed) |
| Spec readiness checklist | `docs/specs/README.md` |

**Decision governance:** Q15/Q16 (deny-by-default manifest, deny-wins |
| evaluation, sandbox boundary, network allow-list, audit of |
| invocations and denials, explicit confirmation for sensitive |
| actions) are closed and linked above. These decisions constrain |
| this spec as stated in each decision note's "Constraint on future |
| specs" section.
