Q2 Benchmark Results
====================
Command: java -cp ".bench-classes:$CP" AuditPersistenceBenchmark
Hardware: Android 17, API 37, ABI arm64-v8a, app UID 10450
JVM: OpenJDK 21.0.13, OpenJDK 64-Bit Server VM
SQLite JDBC: org.xerial:sqlite-jdbc:3.45.0.0 (WAL, synchronous=FULL)
Benchmark: 10,000 measured appends + 500 warmup, single client

Results:
  Appends: 10000
  Total time (s): 0.966
  p50 latency (ms): 0.070
  p99 latency (ms): 0.443
  Throughput (appends/sec): 10350.0

Baseline: p99 <= 10 ms, throughput >= 100 appends/sec
p99 meets baseline: true (0.443 ms vs 10 ms ceiling)
Throughput meets baseline: true (10,350 vs 100/sec floor)
Chain valid after benchmark: true
Total entries: 10500

Conclusion: RPO = 0 (synchronous=FULL durability) is operationally affordable.
The measured p99 is ~22x under the latency ceiling and throughput is ~100x
above the floor, leaving substantial headroom for WAL checkpointing and
OS jitter in production.
