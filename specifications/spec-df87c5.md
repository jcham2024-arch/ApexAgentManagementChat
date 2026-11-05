# Production Readiness Build Plan
## From Safety Kernel to Production Agentic Chat Platform

---

## Purpose

This document is the execution plan for closing the production-readiness gaps identified in the prior review. It sequences the missing work into six phases, each with clear dependencies, deliverables, exit criteria, and test requirements.

The prior build plan covered the foundation (audit determinism, sandbox, tool-call parsing, proposals, secure keys). This plan covers what comes after: reliability, observability, cost governance, layered security, memory, and operations.

Read this as an execution order, not a wish list. Every item here is required for a production deployment. Items are ordered so that each phase unblocks the next and so that the highest-risk failure modes (runaway cost, silent tool failures, undetected injection) are addressed early.

---

## Prerequisite: Decide the Deployment Topology

Before Phase 0, the team must decide one of three topologies. Every subsequent deliverable depends on this choice.

| Topology | Description | Implications |
|---|---|---|
| A. Client-only | Android app calls the provider directly, no backend | Secrets in Keystore, no server-side budget enforcement, no shared circuit breakers, no cross-device memory, no server-side audit |
| B. Client + thin backend | Android calls our backend, backend calls provider | Server-side budgets, circuit breakers, audit, secrets; adds hosting cost and auth |
| C. Full platform | Backend with accounts, multi-device, server-side memory | All of B plus auth, storage, multi-tenancy, operational burden |

**Recommendation for a product that lets the model write files and propose upgrades:** Topology B. Client-only cannot enforce hard budgets or centralized circuit breakers, and cannot keep API keys off the device.

**Decision owner:** product + security. **Decision deadline:** before Phase 0 kicks off. The remainder of this plan assumes Topology B; Topology A omits all server-side deliverables and substitutes local equivalents.

---

## Phase 0 — Operational Baseline

**Goal:** establish the pipeline, the test harness, and the structured logging that every later phase depends on.

**Why first:** without CI, regression tests, and structured logs, no subsequent phase can be verified or debugged.

### Deliverables

**0.1 CI/CD pipeline**

- Build pipeline for the Android app (assemble, lint, unit tests).
- Build pipeline for the backend module (compile, unit tests, integration tests).
- Test gate: PR cannot merge if any test fails.
- Artifact promotion: debug APK and backend container built per commit, tagged per release.
- Secrets in CI from the CI provider's secret store, not committed.

**0.2 Agent-loop test harness**

- A fake `ChatModelPort` that returns scripted sequences (content-only, tool-call, malformed, error, timeout).
- A fake `ToolRegistry` that records calls and can simulate success, failure, and slow responses.
- A deterministic clock and ID generator for reproducibility.
- A test that drives `AgentOrchestrator` through a full trajectory: user message, model tool call, tool result, model final answer.

**0.3 Structured logging**

- JSON-lines log format with consistent fields: `timestamp`, `level`, `event`, `session_id`, `turn_id`, `tool_name`, `duration_ms`, `outcome`.
- No secrets, API keys, or full prompts in logs. Prompts logged as hashes only.
- Log sink configured (stdout for backend, logcat file for Android debug builds).

**0.4 Baseline dependency scanning**

- Automated dependency vulnerability scan in CI.
- Fail build on critical vulnerabilities in transitive dependencies.

### Exit Criteria

- A PR with a broken test fails CI.
- The agent-loop harness drives a full trajectory end to end.
- Every log line is parseable JSON with the standard fields.
- A new dependency triggers a scan result.

### Tests Required

- Harness self-test: scripted trajectory produces expected sequence of tool calls and outcomes.
- Log schema test: assert fields present and no key-like patterns in output.

---

## Phase 1 — Agent Loop Reliability

**Goal:** make the agent loop fail safely, recover, and self-correct. This is the phase that prevents silent tool failures and runaway loops.

**Depends on:** Phase 0 (test harness, logging).

### Deliverables

**1.1 Structured error handling for tool calls**

- Before execution, validate each `ToolCall`: known tool name, argument schema match, argument types correct.
- On validation failure, do not throw. Construct an observation `{"error": "invalid_arguments", "detail": "..."}` and return it to the model in the next turn so it can self-correct.
- On execution exception, capture the exception class and message, return as an observation. Never let a tool exception crash the orchestrator.
- On unknown tool name, return `{"error": "unknown_tool", "available": [...]}` so the model can pick a valid tool.

**1.2 Idempotency keys for write tools**

- Every write tool (`write_file`, `append_file`, `delete_file`, `propose_patch`) accepts an idempotency key.
- If the key has been seen for this session within the retention window, return the cached result instead of re-executing.
- Key derivation: `sha256(session_id | tool_name | canonical_args)`.
- Retention window: duration of the session plus a configurable grace period (default 1 hour).
- Storage: SQLite table on the backend, or a memory map on the client for Topology A.

**1.3 Loop detection**

- Track the last N tool calls per session (default N=5).
- If the same tool is called with the same canonical arguments more than K times in a row (default K=3), halt the loop and inject a system observation: `{"error": "loop_detected", "instruction": "You are repeating the same call. Change approach or return final answer."}`
- If the halt occurs again in the same session, terminate with `LoopTerminatedException`.
- This is the single most important cost-control measure and it belongs in Phase 1, not Phase 3.

**1.4 Circuit breaker for provider and tools**

- Wrap the provider client and each tool in a circuit breaker: closed, open, half-open states.
- Open after M consecutive failures (default 5). Half-open after cooldown (default 30 seconds).
- While open, calls fail fast with a structured error the model can react to.
- Circuit-breaker state is per (session, dependency) so one user's provider outage does not poison another's session.

**1.5 Checkpointing and crash recovery**

- Before each model turn, persist: session ID, turn number, conversation history (or a reference), pending tool call if any, cumulative cost.
- On startup, scan for sessions with `state = "running"` and no heartbeat within the crash window; mark them `interrupted` and offer resume or abandon.
- Resume replays from the last checkpoint; completed tool calls are not re-executed (idempotency from 1.2 guarantees this).

**1.6 Human-in-the-loop approval workflow**

- Extend the consent model from the prior phase to support asynchronous approval.
- When a tool requires approval, the orchestrator persists state `awaiting_approval` with the tool call and arguments, and yields.
- Approval endpoint accepts `(proposal_id, decision, reason)` with a compare-and-set on the proposal state so two approvals cannot both succeed.
- Denial returns the reason to the model as an observation.
- Approval timeout (default 24 hours) transitions to `expired`.

### Exit Criteria

- An invalid tool-call argument causes an observation, not an exception.
- Replaying the same tool call with the same idempotency key does not re-execute the write.
- A repeated identical tool call is detected and halted within K+1 calls.
- A simulated provider outage trips the circuit breaker; a second session with a healthy provider continues normally.
- Killing the process mid-turn and restarting resumes from the last checkpoint without duplicating writes.
- A concurrent-approval race causes exactly one execution.

### Tests Required

- Argument validation: one test per malformed case (missing, wrong type, extra key).
- Idempotency: replay produces same result and exactly one side effect.
- Loop detection: N identical calls trigger halt; N-1 do not.
- Circuit breaker: state transitions under failure and recovery.
- Checkpoint: kill-and-resume scenario with a side-effecting tool.
- Approval race: two concurrent approvals produce one execution.

---

## Phase 2 — Observability and Cost Governance

**Goal:** make the system observable end to end and enforce hard cost limits. Without this phase, you cannot operate the product.

**Depends on:** Phase 1 (structured events to instrument).

### Deliverables

**2.1 OpenTelemetry instrumentation**

- Adopt the OpenTelemetry GenAI semantic conventions for spans and attributes.
- Emit one span per model call: model name, provider, prompt token count, completion token count, latency, finish reason, error.
- Emit one span per tool call: tool name, argument hash, outcome, latency, side effects.
- Emit a parent span per turn and a root span per session, giving a full trajectory tree.
- Export to an OTel-compatible backend (Jaeger, Tempo, Honeycomb, or equivalent). Sampling: 100 percent in dev, configurable in prod (start at 10 percent).

**2.2 Token and cost accounting**

- Capture token usage from each provider response (input, output, total).
- Store per-turn, per-session, and per-user cumulative usage.
- Cost calculation from a configurable price table per provider and model.
- Expose current session cost to the UI.

**2.3 Hard budgets and enforcement**

- Configurable limits: max tokens per turn, per session, per user per day; max cost per session, per user per day.
- Enforcement occurs before the provider call (check remaining budget) and after (record actual usage).
- When a limit is hit, the session transitions to `budget_exhausted` with a user-readable reason. The model receives a final-turn instruction to close out cleanly.
- On Topology B, budgets are enforced server-side and cannot be bypassed by the client. On Topology A, enforcement is client-side and best-effort.

**2.4 Rate limiting**

- Per-user request rate and token rate (requests per minute, tokens per minute).
- Per-tool rate limit (calls per minute) so a single tool cannot be hammered.
- On Topology B, enforced at the API gateway or in-process with a shared store.

**2.5 Audit trail extension**

- Move from the hash chain in the prior phase to an append-only, tamper-evident log.
- Each record includes: timestamp, session ID, turn, actor (user or model), event type, event data (with secrets scrubbed), previous-record hash, this-record hash.
- Records are stored in a write-once table (or equivalent). Deletion is not permitted at the application layer.
- A verify endpoint re-walks the chain and reports the first inconsistency.

**2.6 Alerting**

- Alerts on: provider error rate above threshold, circuit breaker open, average latency regression, budget exhaustion rate, loop termination rate, audit verify failure.
- Alerts route to the on-call channel. Each alert has a runbook link.

### Exit Criteria

- A full turn produces a traceable span tree with model, tool, and turn spans.
- Token usage and cost appear for every completed turn.
- A budget limit hit terminates the session cleanly with a user message.
- Rate limit rejects the (N+1)th request within the window.
- Audit verify passes on a fresh chain and fails after a tampered record.
- Alerts fire in a staging drill for each configured condition.

### Tests Required

- Span attribute presence for model and tool spans.
- Cost calculation correctness against a fixture price table.
- Budget enforcement: exactly one over-budget call is rejected, previous calls succeed.
- Rate limit boundary: N succeeds, N+1 fails.
- Audit verify: pass on clean, fail on tamper at each record position.
- Alert firing in a simulated condition.

---

## Phase 3 — Security Hardening

**Goal:** defend against prompt injection, secret exfiltration, and parameter abuse. The prior phase guarded against path traversal; this phase guards against model manipulation and output leakage.

**Depends on:** Phase 0, Phase 1 (structured error observations to feed guardrail decisions).

### Deliverables

**3.1 Guardrail layer**

- A component that sits between user input, model output, and tool execution.
- Input guardrails: detect prompt-injection patterns, block requests that try to override system instructions, mask PII in prompts before they reach the provider.
- Output guardrails: scan model output for leaked secrets (regex plus entropy heuristics), for tool arguments that violate policy, and for attempts to encode URLs or paths the user has not approved.
- Behavioral contracts: enforce max tool calls per turn, deny-listed tools, allowed URL hosts. A violation terminates the turn with an explicit reason.
- Guardrail decisions are logged and audited.
- Guardrails fail closed: if the guardrail itself errors, the request is rejected, not passed through.

**3.2 Tool output scrubbing**

- Before returning a tool result to the model, scrub known secret patterns: API key formats, JWT shapes, private key headers, bearer tokens, environment variable names matching `*_KEY`, `*_SECRET`, `*_TOKEN`.
- Before writing tool output to logs or audit, apply the same scrubbing.
- Scrubbing is centralized in one module used by all tool paths. Do not allow per-tool implementations.

**3.3 Tool parameter constraints**

- Every tool declares a schema with explicit constraints: path must be relative and under sandbox root, URL must be `https`, host must be in the allowlist, content length within quota.
- Schema validation occurs before execution (Phase 1.1) and again inside the tool (defense in depth).
- A tool that accepts a URL must reject URLs resolving to private IP ranges, localhost, and link-local addresses.

**3.4 Zero-trust sandbox hardening**

- Run sandbox file operations against a directory that is isolated at the OS level where the platform supports it (Android scoped storage; backend container with a bind mount and no network egress).
- Reject symlinks in tool input paths and re-verify target after resolution.
- Reject hard links.
- Enforce a maximum path depth and maximum segment length.

**3.5 Secrets management**

- Backend: API keys and provider credentials in a dedicated secret manager (HashiCorp Vault, AWS Secrets Manager, or equivalent). No secrets in environment files or container images.
- Rotation: scheduled rotation of provider keys, with zero-downtime rollout.
- Client: keys remain in Keystore from the prior phase. On Topology B, the client should not hold provider keys at all; the backend holds them.

**3.6 Injection-resistant prompt design**

- System prompt is a fixed, versioned template. It is not user-editable.
- Tool outputs are delimited with explicit markers and labeled as data in the prompt: `<tool_result tool="read_file" trust="untrusted">...</tool_result>`.
- The system prompt instructs the model to treat content inside `trust="untrusted"` as data, not as instructions.
- The system-prompt hash is recorded in the audit trail per session so a compromised prompt can be identified.

### Exit Criteria

- A prompt-injection fixture set (at least 20 known patterns) is blocked or neutralized.
- A tool returning a string containing a fake API key is scrubbed before reaching the model.
- A tool call with a URL resolving to `127.0.0.1` is rejected.
- A symlink or hard link inside the sandbox is rejected.
- Secrets are absent from logs, audit, and traces in an end-to-end scan.
- Guardrail failure results in request rejection, not pass-through.

### Tests Required

- Injection corpus: each fixture produces a blocked or neutralized outcome.
- Secret scrubbing: each known format is redacted; benign strings are untouched.
- Tool schema: one rejection test per constraint.
- URL validation: private IP, localhost, `http`, non-allowlisted host all rejected.
- Sandbox: symlink, hard link, deep path, long segment all rejected.
- Guardrail fail-closed: an injected guardrail exception causes request rejection.

---

## Phase 4 — Memory and Context Management

**Goal:** retain useful context across sessions without blowing the context window or the budget. This is what turns the agent from a stateless chatbot into a platform.

**Depends on:** Phase 2 (token accounting), Phase 3 (secret scrubbing for memory writes).

### Deliverables

**4.1 Context budgeting**

- Track current context size before each model call.
- Reserve headroom for the response (configurable, default 20 percent of the window).
- When the context approaches the limit, apply a summarization step: summarize the oldest turns into a compact "session summary" and replace them.
- Summarization is model-driven, not heuristic. Summary is itself budgeted.
- Never summarize system prompt, tool definitions, or the current turn.

**4.2 Selective persistent memory**

- A per-user store of reusable context: task specifications, data schemas, tool configuration preferences, output constraints, past decisions with rationale.
- Memory is structured, not free text: each memory entry has a type, a key, and a value, plus provenance (session, turn) and a last-used timestamp.
- Memory is retrieved by relevance, not dumped wholesale. Retrieval is deterministic: keyword and type match first, vector similarity optional.
- Memory is user-inspectable and user-editable. Users can view, edit, and delete entries. This is required for trust and for GDPR-style deletion.
- Memory writes go through the same secret scrubbing as tool output.

**4.3 Session resumption**

- A resumed session includes the session summary from 4.1 as context, so the agent does not restart cold.
- Resumption is opt-in from the UI; abandoned sessions age out after a configurable window.

**4.4 Memory retention and deletion**

- Per-entry TTL where the memory type warrants it (e.g. transient schemas).
- User-triggered deletion removes the entry from the store and from any cached retrieval index.
- Memory is included in the user data export.

### Exit Criteria

- A long conversation does not overflow the context window; summarization triggers before the limit.
- A user can see, edit, and delete memory entries.
- A resumed session retains prior context via summary.
- Memory never contains secrets (verified by scan).

### Tests Required

- Summarization trigger: conversation reaches threshold, summary generated, context length reduced.
- Retrieval: relevant memory returned for a matching query; irrelevant memory not returned.
- User deletion: entry gone from store and from retrieval results.
- Secret scrubbing on memory writes.

---

## Phase 5 — Production Deployment and Operations

**Goal:** run the platform reliably and cheaply in production. This phase is the transition from "works on a developer machine" to "operates in production."

**Depends on:** all prior phases.

### Deliverables

**5.1 Deployment topology implementation**

- Backend containerized (Docker image, multi-stage build, distroless or minimal base).
- Stateless request handling where possible; shared state (sessions, budgets, circuit breakers, idempotency) in Redis or Postgres.
- Session affinity strategy documented and implemented if stateful parts exist.
- Horizontal scaling configuration with autoscaling rules based on request rate and queue depth.

**5.2 Health and readiness**

- `/healthz` liveness: process is running.
- `/readyz` readiness: dependencies reachable (database, Redis, secret manager, provider).
- Metrics endpoint compatible with the chosen monitoring stack.

**5.3 Secrets and configuration**

- All configuration through environment variables or a config service; no hardcoded values.
- Secrets fetched at startup and cached with a short TTL and refresh.
- Startup fails if required secrets are missing; startup logs which configuration keys are set, not their values.

**5.4 Database migrations**

- Versioned migrations, applied automatically at deploy, tested against a staging database.
- Rollback path documented per migration.
- No destructive migrations without a two-step (add new, migrate, remove old) process.

**5.5 Backup and restore**

- Automated backup of the database and audit chain.
- Restore procedure tested quarterly.
- Restore into staging is a phase exit criterion.

**5.6 Evaluation harness**

- A benchmark set of agent trajectories that exercise each tool and each failure mode.
- An automated run on every release candidate: pass, latency, cost, tool success rate.
- A regression in pass rate or cost above threshold blocks release.

**5.7 Incident response**

- Runbooks for each alert from Phase 2.6.
- On-call rotation defined.
- Post-incident review template in place.

### Exit Criteria

- Deploy from CI to staging on every merge; to production on release tag.
- Health and readiness endpoints return correct status under normal and degraded conditions.
- Staging restore from backup succeeds.
- Evaluation harness runs on release candidates and blocks on regression.
- Each alert has a runbook.

### Tests Required

- Health endpoint returns 200 when healthy, 503 when a dependency is down.
- Config validation rejects a missing required secret at startup.
- Migration applies forward and back on a seeded database.
- Restore from backup into a fresh environment.
- Evaluation harness pass rate within threshold on the baseline set.

---

## Cross-Cutting Requirements

These apply to every phase and are checked at each exit review.

| Requirement | Applies To | Check |
|---|---|---|
| Every new component has unit and integration tests | All phases | PR review checklist |
| Every new external call is traced | Phases 2+ | Span emitted in staging |
| Every new write path uses idempotency and scrubbing | Phases 1, 3 | Test present |
| Every new user-facing state is persistent and resumable | Phases 1, 4 | Kill-and-resume test |
| Every new secret path is audited | Phases 2, 3 | Audit record present |
| Every new failure mode has a user-readable message | All phases | UI text review |
| Every new configuration key is documented and validated | All phases | Config schema |

---

## Sequencing Summary

```
Prerequisite: Topology decision (A / B / C)
Phase 0: CI, test harness, structured logging, dependency scanning
Phase 1: Tool-call validation, idempotency, loop detection, circuit breakers,
         checkpointing, async HITL approval
Phase 2: OpenTelemetry, token/cost accounting, hard budgets, rate limiting,
         audit chain upgrade, alerting
Phase 3: Guardrails, output scrubbing, tool schema constraints, sandbox hardening,
         secrets management, injection-resistant prompt design
Phase 4: Context budgeting, selective persistent memory, session resumption,
         memory retention and deletion
Phase 5: Containerized deployment, health/readiness, config, migrations,
         backup/restore, evaluation harness, incident response
```

Each phase is a release boundary. Do not begin a phase until the prior phase's exit criteria are met.

---

## Estimated Effort and Team Shape

Effort is a planning aid, not a commitment. It assumes a small team of two to four engineers with one part-time security reviewer and one part-time product owner.

| Phase | Duration | Primary Roles | Parallelizable |
|---|---|---|---|
| Topology decision | 1 week | Product, Security, Backend lead | No |
| Phase 0 | 2 weeks | Backend, Android, DevOps | Partially |
| Phase 1 | 4 weeks | Backend, Android | Yes, by deliverable |
| Phase 2 | 4 weeks | Backend, DevOps, Data | Yes, by deliverable |
| Phase 3 | 4 weeks | Security, Backend | Partially |
| Phase 4 | 4 weeks | Backend, Data | Partially |
| Phase 5 | 4 weeks | DevOps, Backend | Yes |

Total: approximately 23 weeks to production readiness from a clean start, assuming no blockers.

---

## Risk Register

| Risk | Phase Where It Bites | Mitigation |
|---|---|---|
| Topology decision delayed | All | Hold decision before Phase 0 starts; pick B if uncertain |
| Injection corpus incomplete | 3 | Source from public corpora plus internal red-team; review quarterly |
| Budget enforcement bypassed on client | 2 | Topology B; on A, treat budget as advisory and disclose to user |
| Loop detection false positives | 1 | Tune K per tool; allow user override with audit record |
| Circuit breaker false positives | 1 | Per-session state; half-open probes; tunable thresholds |
| Memory becomes a leak vector | 4 | Scrubbing on write, user visibility, deletion API, retention TTL |
| Evaluation harness becomes stale | 5 | Add a new trajectory per shipped feature; prune yearly |
| Audit chain performance at scale | 2, 5 | Batch writes, async verify, separate read path |
| Cost of observability exceeds cost of product | 2 | Sample traces at 10 percent in prod; aggregate metrics, don't store all |

---

## Metrics to Track

Once Phase 2 lands, these are the platform health metrics. They are also the release health metrics.

- **Reliability:** tool success rate, tool failure rate by tool, loop-termination rate, circuit-breaker open rate, crash-resume rate.
- **Cost:** average cost per session, p95 cost per session, budget-exhaustion rate, loop-detected cost savings (counterfactual).
- **Latency:** p50 and p95 turn latency, provider latency, tool latency by tool.
- **Security:** guardrail block rate, scrubbed-secret count, injection attempts detected, approval denial rate.
- **Quality:** evaluation pass rate, model self-correction rate (error observations followed by corrected call).
- **Operations:** deploy frequency, change failure rate, restore test pass rate, alert-to-ack latency.

---

## Definition of Done

The platform is production ready when all of the following are true:

- [ ] Every phase exit criterion is met and independently reviewed.
- [ ] The evaluation harness runs on every release candidate and blocks on regression.
- [ ] A new engineer can deploy to staging following the runbook without assistance.
- [ ] A security review signs off on the guardrail, scrubbing, sandbox, and secrets implementations.
- [ ] A staging restore from backup completes within the documented window.
- [ ] The on-call rotation has acknowledged each runbook.
- [ ] Cost and reliability dashboards are live and have baselines.
- [ ] The audit chain passes verification and has been externally reviewed for tamper evidence.
- [ ] The user data export and deletion paths are exercised end to end.
- [ ] Every alert has fired at least once in a staging drill and routed correctly.

---

## What This Plan Does Not Cover

The following are out of scope for this plan and belong in a separate product roadmap:

- Multi-tenancy and account management (assume single-user or single-tenant per deployment for now).
- Billing and subscription.
- Localization and internationalization.
- Model fine-tuning or custom model hosting.
- Client apps beyond Android.
- Marketplace or tool ecosystem for third-party tools.

If any of these become requirements, revisit the topology decision first; several of them flip the topology to C.

---

## Summary

The prior phases produced a safety kernel and a functional product loop. This plan adds the six layers a production platform requires: operational baseline, loop reliability, observability and cost governance, security hardening, memory and context, and production deployment. Each phase is a release boundary with explicit exit criteria and tests. The plan is achievable by a small team in roughly six months, assumes Topology B (client plus thin backend), and treats the topology decision as the first thing the team must resolve.