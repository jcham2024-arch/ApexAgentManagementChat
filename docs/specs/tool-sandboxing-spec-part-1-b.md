# BUILD-SPEC: Tool Sandboxing and Capability Restrictions (`engine-tools-impl`) — Part 1-b of 3

**Source:** docs/specs/tool-sandboxing-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 1-b of 3
**Cross-reference:** See `tool-sandboxing-spec-part-1-a.md` for sections 1-4.
**Cross-reference:** See `tool-sandboxing-spec-part-2.md` for sections 9-15.

## 5. Dependency Ceiling and Justification

| Dependency | Scope | Justification |
| :--- | :--- | :--- |
| `com.apex:engine-core` | Compile | Tier 0 ports and |
| | | `ToolExecutorPort`, `ToolCall`, `ToolResult` |
| | | (project dependency). |
| `com.apex:engine-tools` | Compile | Tool metadata and |
| | | policy records |
| | | (project dependency, Tier 0). |
| `org.jspecify` | Compile | Null-safety |
| | | annotations per project |
| | | standards. |
| `org.junit.jupiter:junit-jupiter` | Test | Test framework per |
| | | project standard. |
| `org.assertj:assertj-core` | Test | Assertions per |
| | | project standard. |
| `com.tngtech.archunit:archunit-junit5` | Test | Architectural |
| | | enforcement per project |
| | | standard. |

**No new external dependencies.** All dependencies are project-internal or
already-approved.

**Tier 0 purity:** `engine-tools-impl` is Tier 1 — I/O allowed here.
`engine-tools` (Tier 0) hosts tool metadata and policy records only, no
I/O. `CorePurityTest` passes in all Tier 0 modules.

## 6. Implementation Design

### 6.1 Default posture

**Deny by default.** Every tool not explicitly declared in the agent's
capability manifest is rejected. Every absent manifest field means an
empty allow-list — nothing permitted.

### 6.2 Capability manifest

A per-agent manifest listing allowed tools, allowed hosts, and allowed
read paths. The manifest is compiled into the runtime's tool gate, not
consulted informally. An unenforced manifest is worse than no manifest
(false assurance).

### 6.3 Deny-wins evaluation

Deny rules take precedence over allow rules. Explicit deny rules are
evaluated independently; deny always wins across scopes.

### 6.4 Sandbox boundary

| Tool | Sandbox restriction |
|------|---------------------|
| `read` | Read-only paths; path-traversal |
| | blocked; resolve against declared root |
| `write` | Write-only paths; no exec |
| | bits; size cap; resolve against |
| | declared root |
| `list` | Directory listing only; |
| | depth cap |
| `http` | Allow-listed hosts; |
| | timeout; no local/link-local |
| | addresses (SSRF prevention) |
| `run` | Command allow-list; no |
| | shell interpolation |
| | (`/bin/sh -c` with user input); |
| | timeout |

### 6.5 Network allow-list

Outbound HTTP restricted to an allow-list of hosts. All other egress
denied by default. Host allow-list is empty by default in v1, populated
per deployment via the capability manifest (no hardcoded production
hosts).

### 6.6 Audit

Every tool invocation (tool name, args redacted, outcome, duration) and
every permission decision (including denials) is logged into the
hash-chained audit log via `AuditPort`. Denials are the signal that the
policy is working — they belong in the audit chain.

### 6.7 Sensitive actions

Explicit confirmation required for actions such as infrastructure
changes or pushes. A sensitive action without confirmation is blocked.

### 6.8 Timeouts and cancellation

Every tool execution bounds wall-clock time; on timeout, return
`ToolResult.failure(timeout)`. `CancellationToken` checked before
starting and at checkpoints; if cancelled, return cancelled result
without side effects where possible.
