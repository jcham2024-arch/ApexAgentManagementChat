# PHASE-S2.0-MODULE-SKELETONS-SESSION.md — Session Package, Part 1 of 3

**Project:** Apex Agent Management Chat
**Phase:** S2 (coding kernel), sub-phase S2.0 (module skeletons)
**Part 1 of 3:** reading protocol, context deltas, scope.

Parts: `PHASE-S2.0-MODULE-SKELETONS-SESSION.md` (this file) ·
`PHASE-S2.0-MODULE-SKELETONS-POMS.md` (§3.1–§3.5) ·
`PHASE-S2.0-MODULE-SKELETONS-MODULES.md` (§3.6–§3.9, exit criteria,
commits). Read all three in order; together they are the original S2.0
session package, split to honour the 200-line rule in `AGENT_RULES.md`
§2.13. Content is unchanged apart from these part headers.

## 0. Reading protocol

Read in this exact order before writing any code:

1. `AGENT_RULES.md` (in repo, always-loaded behavior contract)
2. `CONTEXT.md` (in repo, always-loaded project map and file index)
3. **This file** (the complete S2.0 session instruction)

Do not begin until all three are read.

If any of the three is missing or unreadable, stop and report which.

---

## 1. Context notes — deltas between `CONTEXT.md` and this session

`CONTEXT.md` was written for the whole project. Some of it does not match the current state of the repo. These notes are the authoritative resolution for S2.0. Where a note conflicts with `CONTEXT.md`, this note wins. Do not stop over these — they are intentional, not errors.

### 1.1 The spec is not yet committed

`docs/apex-chat-spec/` is not in the repo. `CONTEXT.md` §2.C lists eleven spec files that do not exist yet.

**S2.0 does not need the spec.** Do not read it, do not create it, do not fail the reading protocol over its absence during S2.0. The spec will be pasted before S2.2 begins.

### 1.2 Eight new module names

S2 introduces modules that are not yet in `CONTEXT.md` §4. These are the authoritative module names for S2:

```
apex-core (parent aggregator)
apex-core/engine-core      (Tier 0)
apex-core/engine-planner   (Tier 0)
apex-core/engine-tools     (Tier 0)
apex-core/engine-store     (Tier 0)
engine-tools-impl          (Tier 1)
engine-llm-http            (Tier 1)
engine-store-sqlite        (Tier 1)
engine-cli                 (Tier 1)
```

`CONTEXT.md` §4 gets updated in S2.7 to reflect these. For S2.0, use these names exactly.

### 1.3 Enforcer rule restructure

S1.2 added the `maven-enforcer-plugin` block to `apex/pom.xml` (root parent). That block **moves in S2.0** to `apex-chat/pom.xml` and a new `apex-core/pom.xml`.

Tier 1 modules (`engine-tools-impl`, `engine-llm-http`, `engine-store-sqlite`, `engine-cli`) declare their own Enforcer block explicitly in their own `pom.xml`. The excluded groups are the same; only the location changes.

**Why:** Tier 0 modules must be provably pure. Tier 1 modules may use JDK I/O but must not use Spring/Google/Embabel/Jackson/Micrometer. Making each tier declare its ban in its own group parent means a future engineer cannot accidentally remove the ban by editing the wrong POM.

### 1.4 JDK imports are not Maven dependencies

Tier 1 modules use `java.io`, `java.nio.file`, `java.net.http`, `java.sql`. These are JDK modules, not Maven artifacts. They do not trigger the Enforcer. Do not add a Maven dependency for HTTP, SQLite, or file I/O. If a future sub-phase needs an external library, it goes into `docs/PORT-CATALOGUE.md` in S2.1.

### 1.5 CI `jdeps` gate narrowed to Tier 0

S2.0 §3.9 narrows the `jdeps` gate to Tier 0 jars only. If the gate stays broad, Tier 1 jars will fail for using `java.net.http` — which is allowed. Tier 1 has its own Enforcer block, so the `jdeps` narrowing is safe.

### 1.6 No tests in S2.0

The green CI at the end of S2.0 proves the module structure compiles. That is the only thing it proves. Do not add tests to "prove the skeleton works" — the skeleton has no behavior to test.

### 1.7 Module dependencies in S2.0

Only three Tier 1 modules declare a Maven dependency on `apex-core/engine-core`: `engine-tools-impl`, `engine-store-sqlite`, `engine-cli`. `engine-llm-http` does not — it depends only on the `ChatModelPort` interface, which lands in S2.1.

This means `engine-llm-http` will have an unused placeholder dependency on `engine-core` until S2.1 populates the interface. That is intentional. Add the dependency anyway so S2.1 does not need to touch the POM.

### 1.8 Prerequisite: S1.2 must be merged

S1.2 must be merged to `main`. `mvn -B test` must be green. `apex-chat-common` must exist with `TenantId` and `CorePurityTest`. The `maven-enforcer-plugin` block must currently be in `apex/pom.xml`.

If any of these is false, stop and report — do not proceed.

### 1.9 Existing directory

`docs/supplements/` may exist from an earlier plan. It is not authoritative. Do not read it, do not modify it, do not delete it in S2.0. A separate cleanup commit will handle it later.

---

## 2. Scope

S2.0 creates **module skeletons only**. Every new module has:

- A `pom.xml` with the correct parent, dependency set, and Enforcer configuration
- A `package-info.java` with `@NullMarked` and a one-paragraph purpose
- An empty `src/main/java` and `src/test/java` directory
- A `README.md` listing tier, allowed dependencies, ports consumed, ports implemented

No Java classes beyond `package-info.java`. No tests. No port interfaces (S2.1). No planner code (S2.2). No tool implementations (S2.3).

S2.0 also:
- Moves the Enforcer rule from `apex/pom.xml` to `apex-chat/pom.xml` and the new `apex-core/pom.xml`
- Narrows the CI `jdeps` gate to Tier 0 jars only
- Creates `docs/INVARIANTS.md` with a stub listing all invariant IDs (K1–K7, E1–E5, C1–C4) and "Test" column pointing to the phase that will implement each

---

**End of part 1.** Continue in `PHASE-S2.0-MODULE-SKELETONS-POMS.md`.
