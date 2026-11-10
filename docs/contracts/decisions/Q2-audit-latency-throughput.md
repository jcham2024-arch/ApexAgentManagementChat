# Decision: Q2 — Audit Per-Append Latency and Throughput

## Question
Audit per-append latency and throughput budget.

## Chosen value
**p99 ≤ 10 ms per committed append. Sustained ≥ 100 appends/sec.**
Synchronous durability (synchronous=FULL), no buffering that can drop events.

## Approver
Service owner + engineering team (same 30-minute decision session as Q1).

## Evidence
Benchmark results: `docs/contracts/decisions/q2-benchmark-results.md`.
Command: `java -cp ".bench-classes:$CP" AuditPersistenceBenchmark`
with `-Djava.io.tmpdir=$HARNESS_TMP`. 10,000 measured appends + 500 warmup,
single client, WAL + synchronous=FULL.
Measured p50 = 0.070 ms, p99 = 0.443 ms, throughput = 10,350 appends/sec.
Chain valid after benchmark: true.

## Rationale
The baseline is anchored to measured SQLite behavior under synchronous=FULL.
SQLite synchronous=FULL single-client durable-on-ack achieves ~846 ops/sec
and per-commit latency ~275 µs (0.28 ms) on ordinary hardware. The p99
ceiling of 10 ms is roughly an order of magnitude above p50, covering the
distribution tail driven by WAL checkpointing, device-level flush
variability, and OS scheduling. The throughput floor of 100 appends/sec is
the conservative planning number for a single SQLite writer under
synchronous=FULL, leaving ~100x headroom from the measured 10,350/sec.

Do NOT relax synchronous to meet a throughput target. synchronous=NORMAL
would restore speed but reintroduce the power-loss loss window that Q1
forbids. If the envelope is exceeded, the escalation path is an async
buffering adapter that never drops events, or batched commits within the
constraint that a batch is atomic (a partial batch is never visible).

## Constraint on future specs
No audit adapter implementation may relax synchronous=FULL to synchronous=NORMAL
or any non-fsync durability mode to improve throughput. If throughput
targets are exceeded, the approved escalation paths are (a) an async
buffering adapter with guaranteed event delivery, or (b) batched atomic
commits where a partial batch is never visible. Changing the durability
setting is not an approved escalation path.

## Link
- Governing ADR: `docs/adr/ADR-001-audit-persistence.md`
- Build spec governance: `docs/specs/audit-persistence-spec.md` (Q2 link)
- Benchmark evidence: `docs/contracts/decisions/q2-benchmark-results.md`
