# INVARIANTS.md — Testable Claims

Each invariant is a claim the code must satisfy. The Test column names
the phase that will implement the test. An invariant is not "proven"
until its test exists and is green.

## Kernel (K)
| ID | Claim | Test |
|---|---|---|
| K1 | No dynamic JVM classloading | `DynamicClassloadingGuardTest.noDynamicClassloading` |
| K2 | No shell interpolation | `RunCommandToolTest.doesNotInterpretShellMetacharacters_K2` |
| K3 | Kernel invariants are compiled constants | `ImmutableKernelInvariantsTest.securityPolicyIsNotEmpty` | `ImmutableKernelInvariantsTest.securityPolicyContainsMandatoryRefusal` |
| K4| `RealPathJailTest.resolveExistingFileInsideRoot` | `RealPathJailTest.rejectsParentTraversal` | `RealPathJailTest.rejectsAbsolutePathOutsideRoot` |
| K5 | Kill process tree via ProcessHandle.descendants | `RunCommandToolTest.enforcesTimeoutAndKillsTree_K5` |
| K6 | Cooperative cancellation ≤100ms | `PlanLoopCancellationTest.cancellationIsHonouredWithinOneHundredMilliseconds` (S2.2) · `OpenAiCompatibleProviderStreamTest.cancellationStopsBeforeAllChunksArrive` (S2.4) |
| K7 | OS injection guarded by dead-man + rate limit | `InputInjectionGuardTest` |

## Evolution (E)
| ID | Claim | Test |
|---|---|---|
| E1 | Sentinel runs on every writeWorkspaceFile | S5 |
| E2 | Capability escalation requires grant | `RealTaskRunnerTest.l4ToolBlocksWhenApprovalDenied` | `RealTaskRunnerTest.l4ToolExecutesWhenApprovalGranted` |
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
