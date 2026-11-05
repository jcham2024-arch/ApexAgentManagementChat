# PHASE-S1-FOUNDATION.md

**Version:** 1.0 · **Phase:** S1 · **Router only**

## 0. What this file is

S1 is a split phase. Two sub-phases, each ending in a green CI. This
file names them and the exit criteria for S1 as a whole.

## 1. Reading protocol

Load in this order:

1. `AGENT_RULES.md` — behavior rules
2. `CONTEXT.md` — project map
3. This file (the router)
4. **The sub-phase file named by the human for this session**

The human will name exactly one sub-phase file per session. If they
name two, stop and report.

## 2. Sub-phases

| Sub-phase | File | Scope | Ends with |
|---|---|---|---|
| S1.1 | `PHASE-S1.1-SKELETON.md` | Root POM, `apex-chat-common`, `TenantId`, tests, CI | `mvn test` green |
| S1.2 | `PHASE-S1.2-GATES.md` | Maven Enforcer, `jdeps` CI, ArchUnit | All three gates pass on the S1.1 tree |

## 3. S1 exit criteria (both sub-phases must be green)

A. `mvn -B test` passes on `main`.
B. CI runs on every push and reports green.
C. `apex-core-no-spring.jar` is produced and inspected with `jdeps`.
D. Maven Enforcer fails on a deliberate forbidden-dependency test.
E. ArchUnit `CorePurityTest` fails on a deliberate forbidden-import
   test.
F. No file is missing. No file is extra.

## 4. Do NOT do in S1

- Do not create any port interface. Ports land in S2 with their first
  implementation.
- Do not create `engine-core`, `engine-planner`, `engine-tools`,
  `engine-store`, `engine-improve`, `apex-platform/*`, `engine-server`,
  `client`, or any adapter. Those are S2+.
- Do not add Spring, Google, Embabel, Jackson, or Micrometer anywhere.
- Do not create `docs/PORT-CATALOGUE.md`. It lands in S2.
- Do not skip the router. If the human names a sub-phase file, load it;
  if they don't, stop and ask.

## 5. Commit discipline

One branch per sub-phase: `phase/s1.1-skeleton`, `phase/s1.2-gates`.
Squash-merge each to `main` after the human reviews the green CI.

---

**End of router.** Read the sub-phase file the human named.