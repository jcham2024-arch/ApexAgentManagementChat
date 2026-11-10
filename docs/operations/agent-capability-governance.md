# Agent Capability Governance Note

Records the review and approval model for agent capabilities — who approves
a manifest change, what tiering applies, and how the approval is recorded in
the audit chain. This is the gap most agentic deployments fail on in
compliance review: the durable audit chain records what happened, but it
does not decide what an agent is allowed to attempt.

## Governance Model

### Capability Manifest Approval

Every agent operates under a **capability manifest** (defined in the tool
sandboxing spec, Q15/Q16). The manifest lists:
- Allowed tools (empty = nothing permitted)
- Allowed hosts (empty = nothing permitted for outbound HTTP)
- Allowed read paths (empty = nothing permitted)

### Approval Tiers

| Tier | Who approves | Scope | Recorded in audit chain |
|------|-------------|-------|------------------------|
| **Tier 1 — Standard** | Engineering team lead | Read-only tools (`read`, `list`) on approved paths | Yes — every invocation and denial |
| **Tier 2 — Elevated** | Security + engineering lead | Write tools (`write`), network tools (`http` to allow-listed hosts) | Yes — every invocation and denial |
| **Tier 3 — Restricted** | Security + compliance + CISO | Process execution (`run`), infrastructure changes, pushes | Yes — every invocation and denial; explicit confirmation required |

### Approval Workflow

1. **Manifest proposal:** Agent owner submits a capability manifest with
   justification for each tool, host, and path.
2. **Review:** Appropriate tier reviewer(s) assess the manifest against the
   principle of least privilege.
3. **Approval or rejection:** Reviewer approves (with conditions if needed)
   or rejects. The decision is recorded.
4. **Deployment:** Approved manifest is compiled into the runtime tool gate
   (not merely documented — an unenforced manifest is forbidden).
5. **Audit:** Every tool invocation and every permission decision (allow or
   deny) is recorded in the hash-chained audit chain via `AuditPort`.
6. **Review cycle:** Manifests are reviewed at least quarterly or when the
   agent's responsibilities change.

### Manifest Change Approval

Any change to a capability manifest (adding/removing tools, hosts, or paths)
requires re-approval at the same or higher tier than the original approval.
The approval decision, reviewer, and date are recorded. The previous
manifest is retained for audit comparison.

### Sensitive Actions

Explicit confirmation is required for actions such as infrastructure changes
or pushes. A sensitive action without confirmation is blocked and the denial
is recorded in the audit chain.

### What the Audit Chain Does and Does Not Do

| Does | Does Not |
|------|----------|
| Records every tool invocation and denial | Decides what an agent is allowed to attempt |
| Records every approval decision | Approve or reject manifest changes |
| Provides tamper-evident history of all actions | Enforce policy at runtime |
| Enables post-incident reconstruction | Replace human review and approval |

The audit chain is the **record** of what happened. The capability manifest
and approval workflow are the **decision** about what should happen. Both
are required for compliance.

## Compliance Review Readiness

This governance model addresses the most common failure mode in agentic
deployments: deploying agents with broad, unreviewed tool access and no
record of who approved it. By requiring manifest approval at defined tiers
and recording every invocation in the tamper-evident audit chain, the system
provides auditors with both the policy (manifest + approvals) and the
evidence (invocations + denials).
