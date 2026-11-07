# PHASE-S3.0-PLATFORM-BOOTSTRAP-TESTS-CRITERIA.md — Session Package, Part 4 of 4

**Version:** 1.0 · **Part 4 of 4:** §5–§8, tests, exit criteria, commits.
Parts: SESSION · RISK-APPROVAL · PLATFORM · TESTS-CRITERIA, each
`PHASE-S3.0-PLATFORM-BOOTSTRAP-<part>.md` (`AGENT_RULES.md` §2.13).

---
## 5. Tests

S3.0 adds no new tests of its own. All existing tests must still pass
after the `ApprovalRequest` signature change. Specific tests to verify:

1. `engine-core` `PortTest` — one `@Test` per port, still passes
   (single-method ports use lambdas; `ApprovalPort` still works because
   the interface signature did not change, only the request record's
   fields).
2. `engine-core` `PortRecordsTest` — update the `ApprovalRequest` test
   for the new `RiskLevel` field. If a test asserted "rejects blank
   riskLevel", replace it with "rejects null riskLevel".
3. `engine-planner` `AStarPlannerTest` — still passes (RiskLevel not
   moved, only extended).
4. All other tests — unaffected.

If any test fails due to a downstream effect of the `ApprovalRequest`
signature change, fix the call site, do not change the type.

## 6. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `RiskLevel` has `requiresApproval()`; still lives in
      `com.apex.engine.planner`; not moved.
- [ ] `ApprovalRequest.riskLevel` is typed as `RiskLevel`.
- [ ] `engine-core/pom.xml` declares a dependency on `engine-planner`
      (verify it was added in S2.2; add if not).
- [ ] `apex-platform` parent + three child modules exist and build.
- [ ] Each new module has `package-info.java` with `@NullMarked` and
      a `README.md`.
- [ ] `apex/pom.xml` `<modules>` includes `apex-platform`.
- [ ] `CONTEXT.md` §4 lists the four new module rows.
- [ ] `docs/INVARIANTS.md` K1, K3, K4, K7, E2 Test columns say
      `S3.5 pending`.
- [ ] No Spring, Jackson, Micrometer, Google, or Embabel dependency
      added anywhere.
- [ ] No file over 200 lines.
- [ ] All ArchUnit Tier 0 gates still pass.
- [ ] `jdeps` gate inspects only Tier 0 jars (unchanged from S2.0).

## 7. Do NOT add

- Any source file in the platform modules beyond `package-info.java`
- Any Spring, Jackson, Micrometer, Google, or Embabel dependency
- Any JWT library, crypto library, or audit signing library
- Any port implementation in the platform modules (S3.1–S3.3)
- Any change to `engine-tools-impl`, `engine-llm-http`,
  `engine-store-sqlite`, `engine-planner` (beyond the `RiskLevel`
  `requiresApproval()` addition), or `engine-cli`
- Any spec file
- Any file not listed in §4

## 8. Commit

Three commits on `phase/s3.0-platform-bootstrap`:

1. `feat(S3.0): add requiresApproval to RiskLevel, type ApprovalRequest`
2. `build(S3.0): add apex-platform aggregator and three module skeletons`
3. `docs(S3.0): update CONTEXT.md module table, INVARIANTS.md S3.5 placeholders`

Push. Report CI link and confirmation that:

- All existing tests still pass after the `ApprovalRequest` change
- `PortRecordsTest` was updated for the `RiskLevel` field
- No enterprise dependency was added
- The four new modules build

If CI fails twice on the same root cause, stop and report the log.

---

**End of S3.0 session package.**
