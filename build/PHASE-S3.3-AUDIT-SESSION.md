# PHASE-S3.3-AUDIT-SESSION.md — Session Package, Part 1 of 8

**Version:** 1.0 · **Part 1 of 8:** reading protocol, context deltas, prerequisite, scope.
Parts: SESSION · CORE-PORT · AUDIT-CORE · AUDIT-LOG · CLI · AUDIT-TESTS ·
RUNNER-TESTS · CRITERIA, each `PHASE-S3.3-AUDIT-<part>.md`
(`AGENT_RULES.md` §2.13).

---
## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S3.3

Authoritative where they conflict with any prior file. Do not stop over
them.

### 1.1 `AuditPort` signature changes — breaking

S2.1 defined:
```java
void append(AuditEntry entry);
List<AuditEntry> all();
```

S3.3 changes to:
```java
AuditEntry append(String action, Instant at);
List<AuditEntry> all();
boolean verifyChain();
```

Rationale: the caller cannot compute the chain hash correctly without
access to the previous entry's hash. The port owns the chain. The old
`append(AuditEntry)` is removed.

### 1.2 `AuditEntry` gains `previousHash` — breaking

S2.1 defined `(String id, String action, Instant at, String hash)`.
S3.3 adds `String previousHash` (before `hash`). Genesis entries carry
`previousHash = ""`.

Every construction site across the tree must be updated. Search for
`new AuditEntry(`.

### 1.3 HMAC-SHA256 over `previousHash | id | action | atMillis`

The chain hash is computed by `HashChain.compute(key, previousHash, id,
action, atMillis)`. Concatenation uses `|` as delimiter. No external
library — `javax.crypto.Mac` is in `java.base`.

### 1.4 PII redaction happens inside the port, before hashing

`InMemoryAuditLog.append(action, at)` redacts `action` first, then
computes the hash over the redacted form. Two identical events with
different PII produce the same hash — which is correct, because the
stored action no longer distinguishes them.

### 1.5 In-memory only in S3.3

File-backed persistence of the audit log lands in S6 when the server
is wired. `InMemoryAuditLog` keeps the chain in a `List<AuditEntry>`.
`verifyChain()` re-walks it. Production secrets come from
`APEX_AUDIT_SECRET`; dev falls back to a hardcoded 32-byte key.

### 1.6 No spec needed

Router §4 confirms S3.3 does not need `docs/apex-chat-spec/`. Do not
read spec parts. Do not create spec files.

### 1.7 `RealTaskRunner` gains `AuditPort`, writes one entry per tool
call

Before each `tools.execute(call)`, the runner calls
`audit.append("tool_call:" + call.toolName(), Instant.now())`. Exactly
one entry per tool call. Args are **not** in the audit log — they may
contain secrets or PII that redaction might miss; the audit records
the fact of the call, not its content. Results are in `step_log`
(S2.5), not in audit.

### 1.8 `Main.buildRealRunner` wires `InMemoryAuditLog`

Uses `APEX_AUDIT_SECRET` env var if present; falls back to a hardcoded
32-byte dev key. `E2ETest` (S2.7) does not set the env var — it uses
the dev key and passes. Production deployments set
`APEX_AUDIT_SECRET`; the README documents this.

### 1.9 No `docs/INVARIANTS.md` change

E3 ("Sentinel failure → atomic rollback") is proven in S5. Audit chain
verification is an S3 exit criterion (router §5.D), not a named
invariant. `docs/INVARIANTS.md` stays unchanged in S3.3.

## 2. Prerequisite

S3.2 merged to `main`. `mvn -B test` green. `StepLogEntry` has
`tenantId`; `StoragePort.stepsFor` takes `TenantId`;
`RealTaskRunner` takes `TenantId`. If any is false, stop and report.

## 3. Scope

Deliver:

1. `apex-audit`: 4 source files, 3 test files
2. `engine-core`: `AuditPort` signature change (breaking),
   `AuditEntry` gains `previousHash` (breaking)
3. `engine-cli`: `RealTaskRunner` gains `AuditPort`; `Main` wires
   `InMemoryAuditLog`
4. Existing tests updated: `PortTest`, `PortRecordsTest`,
   `RealTaskRunnerTest`
5. New test: `RealTaskRunnerAuditTest`
6. `apex-audit/README.md` documents the chain format
7. **No** `docs/INVARIANTS.md` change
