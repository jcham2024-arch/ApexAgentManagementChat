# Decision: Q15/Q16 — Tool Sandboxing and Capability Restrictions

## Question
Q15: Exact host allow-list for `http` tool (v1).
Q16: Exact command allow-list for `run` tool (v1).

## Chosen value
**Default posture:** Deny by default. Every tool not explicitly declared in the agent's capability manifest is rejected.
**Capability manifest:** A per-agent manifest listing allowed tools, allowed hosts, and allowed read paths. Every absent field means an empty allow-list — nothing permitted.
**Enforcement:** Deny rules take precedence over allow rules; the manifest is compiled into the runtime's tool gate, not consulted informally.
**Sandboxing:** Tool execution isolated in a sandboxed runtime that restricts filesystem and network access to what the manifest permits.
**Network:** Outbound HTTP restricted to an allow-list of hosts; all other egress denied by default.
**Audit:** Every tool invocation and every permission decision logged, including denials, into the hash-chained audit log.
**Sensitive actions:** Explicit confirmation required for actions such as infrastructure changes or pushes.
**Host allow-list (v1):** Empty by default; populated per deployment via the capability manifest (no hardcoded production hosts).
**Command allow-list (v1):** Empty by default; populated per deployment via the capability manifest (no hardcoded production commands).

## Approver
Security + platform engineering (30-minute decision session).

## Evidence
Tool sandboxing has converged on a clear industry consensus: treat tool access like production IAM — deny-all by default, allowlist only what each agent needs. The capability-manifest pattern is the concrete implementation: a single object that every runtime honors uniformly, where every absent field is an empty allow-list and therefore nothing is permitted. The deny-wins rule is consistent across implementations: explicit deny rules are evaluated independently and deny always wins across scopes. The sandboxing requirement is separate from the manifest: a manifest says what is permitted, a sandbox ensures the runtime cannot exceed it even if the tool implementation tries. The audit requirement closes the loop: every invocation and every denial is recorded, which makes the sandbox policy reviewable after the fact.

## Rationale
Deny-by-default prevents the fastest path to unsafe autonomy: giving all agents full tool access "for flexibility." The capability manifest compiled into the runtime gate prevents false assurance from an unenforced manifest. Deny-wins evaluation ensures explicit denies always win. Sandboxing restricts filesystem and network access even if the tool implementation tries to exceed the manifest. Auditing denials (not just successes) provides the signal that the policy is working. Host/command allow-lists are empty by default and populated per deployment — no hardcoded production hosts or commands in v1.

## Constraint on future specs
- Tool access MUST be deny-by-default; every tool not explicitly declared in the capability manifest is rejected.
- The capability manifest MUST be compiled into the runtime's tool gate; an unenforced manifest is forbidden.
- Deny rules MUST take precedence over allow rules.
- Tool execution MUST be sandboxed to restrict filesystem and network access to what the manifest permits.
- Outbound HTTP MUST be restricted to an allow-list of hosts; all other egress denied by default.
- Every tool invocation and every permission decision (including denials) MUST be logged into the hash-chained audit log.
- Explicit confirmation MUST be required for sensitive actions (infrastructure changes, pushes).
- No hardcoded production hosts or commands in v1; allow-lists are empty by default and populated per deployment.
- Do not log only successful invocations; denials belong in the audit chain.

## Link
- Governing ADR: `docs/adr/ADR-007-tool-sandboxing.md`
- Build spec: tool sandboxing build spec (readiness gate blocked on Q15/Q16; now cleared)
