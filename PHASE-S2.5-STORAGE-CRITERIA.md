# PHASE-S2.5-STORAGE-CRITERIA.md — Session Package, Part 5 of 5

**Version:** 1.0 · **Sub-phase of S2** · **Part 5 of 5:** §6–§8, exit criteria, do-not-add, commits.
Parts: SESSION · POM-EXCEPTION · SQLITE-PORT · METADATA-TESTS ·
CRITERIA, each `PHASE-S2.5-STORAGE-<part>.md` (`AGENT_RULES.md` §2.13).

---
## 6. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `SqliteStoragePortTest` passes all 8 tests.
- [ ] `SqliteStoragePortConcurrencyTest` passes all 3 tests.
- [ ] `sqlite-jdbc` is declared in `engine-store-sqlite/pom.xml`; no
      other dependency added.
- [ ] `docs/INVARIANTS.md` is not modified.
- [ ] No file exceeds 200 lines.
- [ ] Enforcer passes on `engine-store-sqlite`; jdeps gate
      (Tier 0 only) unaffected.
- [ ] No changes to any other module.
- [ ] No `created_at` column, no clock injection.
- [ ] Both `engine-tools-impl` and `engine-llm-http` untouched.

## 7. Do NOT add

- Any Spring, Google, Embabel, Jackson, Micrometer dependency
- Any connection pool library (Hikari, DBCP, c3p0)
- Any logging library (`slf4j-simple`, `logback-classic`, `log4j`)
- Any `created_at` or additional schema column
- Any `Clock` field or `Instant` injection
- Any tool, model, planner, or CLI code
- Any ArchUnit test (Tier 1)
- Any jdeps wiring for this module
- Any file not listed in §4 or §5

## 8. Commit

Three commits on `phase/s2.5-storage`:

1. `build(S2.5): add sqlite-jdbc dependency to engine-store-sqlite`
2. `feat(S2.5): add SqliteStoragePort and StorageException`
3. `test(S2.5): add storage round-trip, ordering, and concurrency tests`

Push. Report CI link, per-test-class pass counts, and confirm the
`persistenceAcrossReopen` and `concurrentAppendsSameSession` tests
passed. If CI fails twice on the same root cause, stop and report the
log.

---

**End of S2.5 session package.**
