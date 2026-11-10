# Decision: Q1 — Audit RPO/RTO

## Question
Audit RPO/RTO for the durable audit store.

## Chosen value
**RPO = 0.** RTO = 4 hours.

## Approver
Service owner + risk/compliance (single 30-minute decision session).

## Evidence
Benchmark results: `docs/contracts/decisions/q2-benchmark-results.md`.
Benchmark ran on Android 17, API 37, ABI arm64-v8a, app UID 10450,
JVM OpenJDK 21.0.13, SQLite JDBC 3.45.0.0 with WAL + synchronous=FULL.
Measured p99 = 0.443 ms (ceiling 10 ms), throughput = 10,350 appends/sec
(floor 100/sec). SQLite synchronous=FULL durability enforces RPO = 0: no
committed audit event is lost, including across power loss.

## Rationale
For a tamper-evident HMAC chain, the generic archive default (RPO 24 h,
RTO 24–72 h) is incompatible with the claim that the chain is complete: a
chain that silently drops events still verifies for surviving entries but
the missing entry is undetectable. RPO = 0 requires that every committed
audit event survives process restart and power loss. SQLite synchronous=FULL
is the documented durability setting for "all cases, even following a
sudden power loss"; synchronous=NORMAL in WAL mode may roll back committed
transactions after power loss or OS crash, which is silent event loss and
unacceptable for an audit chain. RTO = 4 h matches the security-audit tier
and is achievable for a single-node SQLite deployment with a documented
restore runbook. Performance evidence from Q2 confirms the durability
setting is operationally affordable (p99 0.443 ms vs 10 ms ceiling).

## Constraint on future specs
Any future audit adapter MUST use synchronous=FULL (or equivalent fsync
durability). Relaxing to synchronous=NORMAL or any non-fsync durability
mode is forbidden, as it reintroduces the power-loss loss window that Q1
forbids. If the performance envelope is exceeded, the escalation path is
an async buffering adapter that never drops events, or batched atomic
commits — never changing the durability setting.

## Link
- Governing ADR: `docs/adr/ADR-001-audit-persistence.md`
- Build spec governance: `docs/specs/audit-persistence-spec.md` (Q1 link)
- Benchmark evidence: `docs/contracts/decisions/q2-benchmark-results.md`
