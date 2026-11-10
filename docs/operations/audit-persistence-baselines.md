# Operational Baselines — Durable Audit Persistence (SQLite)

Records the measured, verifiable operational baselines for the
`apex-audit-persistence-sqlite` module. Objectives (from the Q1–Q3 decision
notes) are stated alongside measured values where available.

## Restore Drill Procedure

### Objective
Verify that the SQLite audit ledger can be restored after a simulated
failure and that the HMAC-SHA256 chain remains verifiable.

### Procedure

1. **Backup** — Copy the SQLite database file:
   ```bash
   cp <audit-db-path> <audit-db-path>.backup-$(date +%Y%m%d-%H%M%S)
   ```

2. **Simulate failure** — Corrupt or delete a middle row directly in the
   database (outside the adapter) to simulate tampering:
   ```bash
   sqlite3 <audit-db-path> "UPDATE audit_log SET action = 'tampered' WHERE sequence = 5;"
   ```

3. **Verify detection** — Run `verifyChain()` through the `AuditPort`.
   Expected result: `false` (chain broken).

4. **Restore** — Replace the database with the backup:
   ```bash
   cp <audit-db-path>.backup-<timestamp> <audit-db-path>
   ```

5. **Re-verify** — Run `verifyChain()` again.
   Expected result: `true` (chain restored and valid).

6. **Resume** — Append a new entry to confirm the chain resumes from the
   restored last hash without a full-table scan.

### Acceptance criteria
- `verifyChain()` returns `false` after tampering.
- `verifyChain()` returns `true` after restore.
- A post-restore append commits to the restored last hash (chain continuity
  preserved).
- RTO measured from step 4 to step 6 must be ≤ 4 hours (Q1 objective).

## Benchmark Command

Validates Q2 baseline: p99 ≤ 10 ms per committed append, ≥ 100 appends/sec,
with WAL + `synchronous=FULL`.

```bash
export HARNESS_TMP=$PWD/.harness-scratch/apex-tmp
mkdir -p $HARNESS_TMP
mvn -B -Dmaven.repo.local=$PWD/.m2 -DargLine="-Djava.io.tmpdir=$HARNESS_TMP" \
  -f project/apex-platform/apex-audit-persistence-sqlite/pom.xml test \
  -Dtest=SqliteAuditLogTest
```

The benchmark itself runs inside `AuditPersistenceBenchmark` (10,000 measured
appends + 500 warmup, single client, WAL + synchronous=FULL).

### Raw results location
- `docs/contracts/decisions/q2-benchmark-results.md` — summary of measured
  p99 latency, throughput, and chain validity.
- Raw benchmark output is captured in the test run console log; store a copy
  under `docs/operations/benchmarks/` if repeated on different hardware.

## Measured Results (Reference Hardware)

| Metric | Objective (Q1/Q2 decision note) | Measured | Status |
|--------|---------------------------------|----------|--------|
| RPO | 0 (no committed event lost) | Enforced by `synchronous=FULL` | Met |
| RTO | ≤ 4 hours | Not yet drilled | Pending first drill |
| p99 append latency | ≤ 10 ms | 0.443 ms | Met |
| Throughput | ≥ 100 appends/sec | 10,350 appends/sec | Met |
| Chain valid after benchmark | true | true | Met |

### Reference hardware
- Android 17, API 37, ABI arm64-v8a, app UID 10450
- JVM: OpenJDK 21.0.13, OpenJDK 64-Bit Server VM
- SQLite JDBC: org.xerial:sqlite-jdbc:3.45.0.0

## Last Drill Record

| Date | Measured RPO | Measured RTO | Result |
|------|-------------|--------------|--------|
| — | — | — | First drill not yet run |

Run the first drill per the procedure above and record the result here.
Auditors check achieved values, not stated objectives.
