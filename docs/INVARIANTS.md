# INVARIANTS.md — Testable Claims

Each invariant is a claim the code must satisfy. The Test column names
the phase that will implement the test. An invariant is not "proven"
until its test exists and is green.

## Kernel (K)
| ID | Claim | Test |
|---|---|---|
| K1 | No dynamic JVM classloading | S3 |
| K2 | No shell interpolation | S2.3 |
| K3 | Kernel invariants are compiled constants | S2.2 |
| K4 | Canonical workspace root via toRealPath | S3 |
| K5 | Kill process tree via ProcessHandle.descendants | S2.3 |
| K6 | Cooperative cancellation ≤100ms | S2.2 |
| K7 | OS injection guarded by dead-man + rate limit | S3 |

## Evolution (E)
| ID | Claim | Test |
|---|---|---|
| E1 | Sentinel runs on every writeWorkspaceFile | S5 |
| E2 | Capability escalation requires grant | S3 |
| E3 | Sentinel failure → atomic rollback | S5 |
| E4 | Commits cannot reference missing blobs | S5 |
| E5 | Branch CAS loses no writes | S5 |

## Contract (C)
| ID | Claim | Test |
|---|---|---|
| C1 | TS types isomorphic to Java OpenAPI | S6 |
| C2 | Zod validation at every TS/Java boundary | S6 |
| C3 | OSC padding `(4 - (len % 4)) % 4` | S8 |
| C4 | ContextCompactor preserves SYSTEM + tool pairs | S4 |
