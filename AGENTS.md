# AGENT_RULES.md — Build Behavior Contract

**Version:** 1.0 · **Always loaded. Governs how you build.**

## 0. What this file is

The behavioral contract. It carries the reading protocol, the hard
prohibitions, the coding rules, the commit format, the verification
protocol, and the stop triggers. Project scope and the file index live
in `CONTEXT.md`. Phase instructions live in `PHASE-S<N>-<NAME>.md`.

This file is under 200 lines and is loaded every session. It is the
contract every phase must obey.

## 1. Session reading protocol

Read in this exact order. Do not write code until at least the first
four are loaded.

1. **This file** — behavior rules. Always.
2. **`CONTEXT.md`** — project map and file index. Always.
3. **`PHASE-S<N>-<NAME>.md`** — build instructions for this session.
4. The **spec parts** named in the phase file
   (`docs/apex-chat-spec/`).
5. **`docs/INVARIANTS.md`** — testable claims. Reference during testing.
6. **`docs/PORT-CATALOGUE.md`** — port registry. Reference when
   implementing or consuming a port.

If any of 1–4 is missing, stop and report which.

## 2. Hard prohibitions

Violating any of these is a build failure. No exceptions.

1. No enterprise dependency in a Tier 0 module. Ever. Not Spring, not
   Google, not Embabel, not Jackson, not Micrometer, not transitively.
   (Tiers defined in `CONTEXT.md` §4.)
2. No `java.io`, `java.nio.file`, `java.net`, `java.lang.reflect`,
   `Runtime`, `ProcessBuilder`, `System.*`, or `Executors.*` in a
   Tier 0 module.
3. No `switch` pattern matching in a Tier 0 module. Use `instanceof` or
   visitors.
4. No `ScopedValue` (preview in Java 21 LTS).
5. No `ThreadLocal` in the agent loop. One read at the HTTP filter
   boundary is allowed; copy into `ExecutionContext` immediately.
6. No mutable collection in a record without `List.copyOf` /
   `Map.copyOf` in the compact constructor.
7. No `Optional` in record components or parameters. Return types only.
8. No `null` in record fields. Use `@Nullable` via JSpecify.
9. No placeholder bodies. No `return null`, no `TODO`, no
   `UnsupportedOperationException`.
10. No disabled or skipped tests.
11. No commit to `main`. Every session is a branch.
12. No dependency on a future phase. Do not scaffold `engine-improve`
    during S1.
13. No file over 200 lines. Split.
14. No method over 40 lines. Extract.
15. No Javadoc block over 15 lines. Move detail to package README.
16. No GPL, AGPL, SSPL, or Commons Clause license, even transitively.

## 3. Coding principles

### Documentation

- Every public class, interface, record, method has Javadoc. Explain
  why, not what.
- Every package has `package-info.java` with `@NullMarked` and one
  paragraph.
- Every Maven module has a `README.md` with: purpose, tier, allowed
  dependencies, ports consumed or implemented, tests, one example.

### Naming and formatting

- Types `PascalCase`. Methods/fields `camelCase`. Constants
  `UPPER_SNAKE_CASE`. Packages lowercase singular nouns.
- No abbreviations except industry-standard (`id`, `url`, `http`,
  `json`, `sql`).
- Records for value objects. Sealed interfaces for closed unions.
  Interfaces for extension.
- 4-space indent. 100-column soft cap. No tabs.

### Context discipline

- Every source file ≤ 200 lines. The load-bearing rule.
- Every method ≤ 40 lines.
- Every Javadoc ≤ 15 lines.
- Every test class ≤ 200 lines.

A file over the limit is a refactor ticket, not a merge candidate.

### Testing

- JUnit 5 + AssertJ + ArchUnit. jqwik for parametric invariants.
- Every public behavior has a test. Every invariant in
  `docs/INVARIANTS.md` has a named test.
- Integration tests tagged `@Tag("integration")`.
- Name tests by behavior: `PermissionGrantExpiryTest`, not
  `PermissionGrantTest`.

### Dependencies

- No dependency added to a Tier 0 module. Ever.
- A new adapter dependency is allowed only in Tier 1 or Tier 2, and
  only if listed in `docs/PORT-CATALOGUE.md`.
- License gate: Apache 2.0, MIT, BSD, ISC only.

## 4. Commit format

```
<type>(<phase>): <subject>

<optional body: what and why>

Refs: S<n>
```

- Types: `feat`, `fix`, `test`, `refactor`, `docs`, `build`, `ci`,
  `chore`.
- Subject imperative, ≤72 chars.
- Branch per phase: `phase/s1-foundation`, `phase/s2-kernel`, etc.

## 5. Verification protocol

Every session:

1. Create branch from `main`.
2. Commit in logical units.
3. Push the branch.
4. Report the CI run link and status.
5. Do not claim tests pass. Say "CI status: green" or "CI status: red"
   with the link. The runtime cannot execute `mvn test` locally; CI is
   the verifier.
6. Stop when exit criteria are met. Report: files (paths), test count,
   CI link, judgment calls, blockers.

## 6. Stop-and-report triggers

Stop and wait for human input when:

1. Phase exit criteria are met.
2. A rule in §2 would be violated by the requested change.
3. A phase file names a type, port, or invariant that does not exist
   in the POJO spec or `docs/PORT-CATALOGUE.md`.
4. A dependency not allowed by §3 is needed.
5. CI fails twice on the same root cause.
6. You find a bug in a prior phase — report, do not silently fix.

Do not stop for: variable names, log levels, test fixture data, import
ordering. Make a reasonable choice; note it in the commit body.

## 7. Tone

Terse. Factual. No emoji. No preamble. Report what you did, what CI
verified, what is blocked. If you made a judgment call, say so and why.

---

**End.** The next file is `CONTEXT.md`.