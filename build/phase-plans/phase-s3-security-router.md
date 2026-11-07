# PHASE-S3-SECURITY.md

**Version:** 1.0 · **Phase:** S3 · **Router only**

## 0. What this file is

S3 adds the platform security layer to the kernel built in S2. After
S3, the kernel is not just functional — it enforces tenant isolation,
authenticates callers, classifies every tool by risk, gates L3+ actions
behind human approval, and writes a tamper-evident audit chain.

S3 is split into six sub-phases. This file names them. The human
attaches exactly one sub-phase file per session.

## 1. Reading protocol

Load in this order:

1. `AGENT_RULES.md`
2. `CONTEXT.md`
3. This file (the router)
4. **The sub-phase file the human attaches this session**
5. **Any invariant IDs the sub-phase file names** (from
   `docs/INVARIANTS.md`)

If the human attaches two sub-phase files in one session, stop and
report.

## 2. Sub-phases

| Sub-phase | File | Scope | Ends with |
|---|---|---|---|
| S3.0 | `PHASE-S3.0-PLATFORM-BOOTSTRAP.md` | Platform POMs, `RiskLevel.requiresApproval`, typed `ApprovalRequest`, `CONTEXT.md` update | `mvn test` green |
| S3.1 | `PHASE-S3.1-AUTH.md` | JWT verifier, RBAC policy, auth filter | Unsigned/expired/wrong-sig tokens rejected; RBAC denies by role |
| S3.2 | `PHASE-S3.2-TENANT.md` | Tenant scoping on `StoragePort` (breaking), RLS advisors | Queries cannot cross tenants |
| S3.3 | `PHASE-S3.3-AUDIT.md` | Chained audit log, PII redaction, audit on every tool call | Audit chain verifies; PII redacted before write |
| S3.4 | `PHASE-S3.4-HITL.md` | `RealTaskRunner` calls `ApprovalPort` before L3+ tools | L3+ blocks until approved; L0–L2 does not |
| S3.5 | `PHASE-S3.5-INVARIANT-PROOFS.md` | K1, K3, K4, K7, E2 proofs | All five invariants have passing tests |

## 3. Modules introduced in S3

Authoritative for S3. `CONTEXT.md` §4 is updated in S3.0 to reflect
these.

| Module | Tier | Role |
|---|---|---|
| `apex-platform` | parent | Aggregator |
| `apex-platform/apex-auth` | 2 | JWT, RBAC, auth filter |
| `apex-platform/apex-tenant` | 2 | Tenant isolation, RLS |
| `apex-platform/apex-audit` | 2 | Chained audit log, PII redaction |

**Modified in S3:**

- `engine-planner`: `RiskLevel` gains `requiresApproval()`. Not moved.
- `engine-core`: `ApprovalRequest.riskLevel` becomes typed `RiskLevel`.
- `engine-store-sqlite`: `StepLogEntry` gains `tenantId` (breaking);
  SQLite schema gains `tenant_id` column.
- `engine-cli`: `RealTaskRunner` gains `ApprovalPort`; `Main` wires a
  terminal-prompt `ApprovalPort`.

## 4. Spec dependency

`docs/apex-chat-spec/` is **not required for S3**. Platform modules
operate on Tier 1/Tier 2 types defined in `engine-core.port`. Layer 1
bridging (spec Parts 4, 9, 10) happens in S6 when the server is built.
Prior router §4 claiming S3 needs spec is superseded by this router.

**Do not read spec parts. Do not create spec files.** If a sub-phase
file says "read spec part X," treat that as a bug and report it.

## 5. S3 exit criteria

A. All six sub-phases green in CI.
B. An L3 tool call in `RealTaskRunner` blocks until `ApprovalPort`
   returns approved.
C. SQLite queries cannot return rows from another tenant.
D. A tampered audit entry fails verification.
E. K1, K3, K4, K7, E2 all have named, passing tests in
   `docs/INVARIANTS.md`.
F. No file over 200 lines. No enterprise dependency in a Tier 0
   module.

## 6. Do NOT do in S3

- No Spring, Google, Embabel, Jackson, Micrometer in Tier 0 or Tier 1.
  Tier 2 modules **may** add them; S3.1 adds `jjwt` (see the sub-phase).
- No GUI, memory router, flywheel, council, node, server.
- No Layer 1 type references (`AgentStep`, `AgentDecision`, etc.).
- No retry logic on model failures (deferred to S10).
- Do not modify any Tier 0 or Tier 1 module except as named in §3.

## 7. Commit discipline

One branch per sub-phase: `phase/s3.0-platform-bootstrap`,
`phase/s3.1-auth`, etc. Squash-merge after human review and green CI.

---

**End of router.**