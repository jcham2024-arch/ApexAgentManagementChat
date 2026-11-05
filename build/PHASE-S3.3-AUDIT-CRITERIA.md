# PHASE-S3.3-AUDIT-CRITERIA.md — Session Package, Part 8 of 8

**Version:** 1.0 · **Part 8 of 8:** §8–§10, exit criteria, do-not-add, commits.
Parts: SESSION · CORE-PORT · AUDIT-CORE · AUDIT-LOG · CLI · AUDIT-TESTS ·
RUNNER-TESTS · CRITERIA, each `PHASE-S3.3-AUDIT-<part>.md`
(`AGENT_RULES.md` §2.13).

---
## 8. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `HashChainTest` passes all 5 tests (including the
      `chainCannotBeForgedWithoutSecret` proof in `InMemoryAuditLogTest`).
- [ ] `PiiRedactorTest` passes all 6 tests.
- [ ] `InMemoryAuditLogTest` passes all 10 tests, including
      `appendRedactsPii`.
- [ ] `RealTaskRunnerAuditTest` passes all 3 tests.
- [ ] `PortTest.auditPortIsImplementable` passes with the new three
      methods.
- [ ] `PortRecordsTest` updated; `auditEntryRejectsNullPreviousHash`
      passes.
- [ ] `RealTaskRunnerTest` updated for the new constructor; still
      passes.
- [ ] `E2ETest` still passes (it uses `Main.run`; `Main` uses
      `DEV_AUDIT_KEY`).
- [ ] `AuditPort` has `append(String, Instant)`, `all()`,
      `verifyChain()`.
- [ ] `AuditEntry` has `previousHash` before `hash`.
- [ ] `RealTaskRunner` writes one audit entry per tool call, before
      execution.
- [ ] `Main.buildRealRunner` wires `InMemoryAuditLog` using
      `APEX_AUDIT_SECRET` if present, `DEV_AUDIT_KEY` otherwise.
- [ ] `apex-audit/README.md` documents the chain format and dev key
      caveat.
- [ ] `engine-cli/README.md` documents `APEX_AUDIT_SECRET`.
- [ ] No file exceeds 200 lines.
- [ ] No enterprise dependency added.
- [ ] `docs/INVARIANTS.md` unchanged.
- [ ] No spec files read or created.

## 9. Do NOT add

- Any Spring, Jackson, Micrometer, Google, or Embabel dependency
- File-backed persistence (S6)
- Monotonic timestamp enforcement (S6)
- Multi-writer coordination (S6)
- Audit on model calls — only tool calls in S3.3
- Any change to `engine-planner`, `engine-tools-impl`,
      `engine-llm-http`, `engine-store-sqlite`, or `apex-auth`
- Any HITL changes (S3.4)
- Any spec file
- Any file not listed in §4–§7

## 10. Commit

Four commits on `phase/s3.3-audit`:

1. `feat(S3.3): change AuditPort to own hash chain; add previousHash to AuditEntry`
2. `feat(S3.3): add HashChain, PiiRedactor, InMemoryAuditLog to apex-audit`
3. `feat(S3.3): wire AuditPort into RealTaskRunner and Main`
4. `test(S3.3): add hash chain, redaction, audit log, and runner audit tests`

Push. Report CI link, per-test-class pass counts, and confirm:
- `InMemoryAuditLogTest.appendRedactsPii` passed
- `InMemoryAuditLogTest.chainCannotBeForgedWithoutSecret` passed
- `RealTaskRunnerAuditTest.auditRecordsToolCall` passed
- `E2ETest` still passes
- `PortRecordsTest.auditEntryRejectsNullPreviousHash` passed

If CI fails twice on the same root cause, stop and report the log.

---
