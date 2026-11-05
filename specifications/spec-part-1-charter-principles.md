# Apex Chat — Implementation Specification, Part 1: Charter & Principles

**Series:** Apex Chat Implementation Specification (10 parts)
**This file:** §0 How to use — §4.4 Interfaces
**Depends on:** nothing
**Used by:** all subsequent parts

---

## 0. How to use this document

Read the entire specification (all 10 parts) before writing any code. Then implement in the **build order** defined in Part 10 §12. At each stage, satisfy the **acceptance criteria** in Part 10 §13 before proceeding. Nothing here should be "interpreted" — where a decision is written as **MUST** or **MUST NOT**, treat it as a hard constraint.

---

## 1. Project charter

Apex Chat is a **pure abstract POJO library** that defines every element of an agentic LLM system — chat, participants, models, prompts, memory, content, thinking, threads, tools, skills, plugins, agents, plans, steps, actions, guardrails, traces, workspaces, artifacts, resources, capabilities, sandboxes — as typed, inspectable, composable objects.

The library is **not** a runtime. It defines *what can exist*. Runtimes implement behavior behind interfaces. Adapters parse external formats. The library itself contains **no I/O, no threads, no network, no OS calls**.

**End products built on this library:**

- A Java agent factory (build-time definitions → materialized agents).
- A TypeScript sandbox runtime (drive pause/resume, checkpoint evaluation, tool routing).
- A Kotlin/Android foreground service with native OS access.
- A JVM server or desktop application.
- A Web/WASM host.

**Every host consumes the same abstract types.**

---

## 2. Non-negotiable principles

These are hard constraints. Violations are defects.

1. **Abstract modules contain no behavior.** No `java.io`, `java.nio.file`, `java.net`, `java.lang.reflect`, `java.util.concurrent`, `Thread`, `System.*`, static mutable state, or `switch` pattern matching. Enforced by ArchUnit + Checkstyle.
2. **Ownership is unambiguous.** Chat owns session-scoped state (roster, thread, memory, workspace, artifacts, resources). Agent owns run-scoped state (descriptor, plan, steps, actions, observations, constraints, guardrails, trace). External stores own durable state.
3. **Chat is a single dynamic container.** There is no `GroupChat`, no `ChatMode` field. Group chat is an emergent property of the roster and policies.
4. **Agents have no direct memory, workspace, artifact, or file fields.** They reach session state through `agent.chat`.
5. **Sealed interfaces for closed unions; records for value objects; interfaces for extension contracts.** No classes with mutable state.
6. **Android desugaring compliance at Java 17 language level.** No Java 21 syntax in abstract modules.
7. **`Optional` is for return types only.** Never in record fields or method parameters.
8. **Nullability via JSpecify** (`@NullMarked` at package level; `@Nullable` only where explicitly optional).
9. **Builders required for records with 6+ fields or optional fields.** See Part 10 §11.1.
10. **Derived state is never stored.** `ChatProfile`, `CapabilityCompatibilityReport` (at run time), `ThreadContextMetrics` (recomputed) are computed on demand.
11. **Every mutation is a typed `ChatMutation` or `AgentMutation`.** No direct state writes; all changes are auditability-first.
12. **Cross-layer references use `common` ref types only.** No module holds another module's concrete types when a `*Ref` will do.

---

## 3. Module topology
