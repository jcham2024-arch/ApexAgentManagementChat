# PHASE-S4.1-WORKING-MEMORY.md

**Version:** 1.0 · **Sub-phase of S4** · **Ends with a bounded working memory backend proven by tests.**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S4-MEMORY-SANDBOX.md` (repo — router)
4. `PHASE-S4.0-MEMORY-SANDBOX-BOOTSTRAP_1.md` (S4.0 bootstrap)
5. **This file**
    }

    @Test
    void clearEmptiesBuffer() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(3);
        mem.add("assistant", "x", T0);
        mem.clear();
        assertThat(mem.size()).isZero();
        assertThat(mem.entries()).isEmpty();
    }

    @Test
    void queryExactMatchScoresOne() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "the quick brown fox", T0);
        MemoryQuery q = new MemoryQuery(
                "quick", Set.of(MemoryKind.WORKING), 10);
        MemoryResult result = mem.query(q);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).score()).isEqualTo(1.0);
        assertThat(result.hits().get(0).kind())
                .isEqualTo(MemoryKind.WORKING);
        assertThat(result.queried()).containsExactly(MemoryKind.WORKING);
    }

    @Test
    void queryCaseInsensitiveScoresLower() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "The QUICK brown fox", T0);
        MemoryQuery q = new MemoryQuery(
                "quick", Set.of(MemoryKind.WORKING), 10);
        MemoryResult result = mem.query(q);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).score()).isEqualTo(0.8);
    }

    @Test
    void queryMissReturnsEmpty() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "alpha", T0);
        MemoryQuery q = new MemoryQuery(
                "zzz", Set.of(MemoryKind.WORKING), 10);
        assertThat(mem.query(q).hits()).isEmpty();
    }

    @Test
    void queryForOtherKindReturnsEmptyAndNoConsultation() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(5);
        mem.add("assistant", "quick", T0);
        MemoryQuery q = new MemoryQuery(
                "quick", Set.of(MemoryKind.SEMANTIC), 10);
        MemoryResult result = mem.query(q);
        assertThat(result.hits()).isEmpty();
        // Backend was not consulted.
        assertThat(result.queried()).isEmpty();
    }

    @Test
    void queryRespectsMaxHits() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(10);
        for (int i = 0; i < 5; i++) {
            mem.add("assistant", "hit " + i, T0);
        }
        MemoryQuery q = new MemoryQuery(
                "hit", Set.of(MemoryKind.WORKING), 2);
        assertThat(mem.query(q).hits()).hasSize(2);
    }

    @Test
    void queryPreservesOldestFirstOrdering() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(10);
        mem.add("assistant", "first hit", T0);
        mem.add("assistant", "second hit", T0);
        mem.add("assistant", "third hit", T0);
        MemoryQuery q = new MemoryQuery(
                "hit", Set.of(MemoryKind.WORKING), 10);
        assertThat(mem.query(q).hits())
                .extracting(MemoryHit::content)
                .containsExactly("first hit", "second hit", "third hit");
    }

    @Test
    void zeroCapacityRejected() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new BoundedWorkingMemory(0));
    }

    @Test
    void negativeCapacityRejected() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new BoundedWorkingMemory(-1));
    }

    @Test
    void addRejectsBlankRole() {
        BoundedWorkingMemory mem = new BoundedWorkingMemory(3);
        assertThatIllegalArgumentException().isThrownBy(
                () -> mem.add("", "content", T0));
    }

    @Test
    void kindIsWorking() {
        assertThat(new BoundedWorkingMemory(3).kind())
                .isEqualTo(MemoryKind.WORKING);
    }
}
```

## 6. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `MemoryBackend` interface exists with `kind()` and
      `query(MemoryQuery)`.
- [ ] `WorkingMemoryEntry` record exists with `(role, content, at)`.
- [ ] `BoundedWorkingMemory` implements `MemoryBackend`,
      `kind() == WORKING`, all methods `synchronized`.
- [ ] `BoundedWorkingMemoryTest` passes all 15 tests.
- [ ] `MemoryTypesTest` (S4.0) still passes.
- [ ] `engine-memory/package-info.java` and `README.md` updated.
- [ ] Enforcer passes on `engine-memory`; `jdeps` gate (Tier 0 only)
      still passes.
- [ ] No file exceeds 200 lines.
- [ ] No enterprise dependency added.
- [ ] `docs/INVARIANTS.md` unchanged.
- [ ] `engine-sandbox` untouched.
- [ ] No spec files read or created.

## 7. Do NOT add

- Any implementation of `MemoryRouter`, `ContextCompactor`, or any
  other `MemoryBackend`
- Any persistent storage (working memory is in-memory only)
- Any embeddings, tokenizers, or scoring beyond the tiered
  exact/substring scheme
- Any LRU or weighted eviction
- Any change to `MemoryQuery`, `MemoryHit`, `MemoryResult`,
  `MemoryRouter`, `ContextCompactor`, or `MemoryKind`
- Any change to `engine-sandbox` or any other module
- Any Spring, Jackson, Micrometer, Google, or Embabel dependency
- Any spec file
- Any file not listed in §4 or §5

## 8. Commit

Three commits on `phase/s4.1-working-memory`:

1. `feat(S4.1): add MemoryBackend interface`
2. `feat(S4.1): add WorkingMemoryEntry and BoundedWorkingMemory`
3. `test(S4.1): add working memory tests; update package-info and README`

Push. Report CI link, per-test-class pass counts, and confirm:
- `BoundedWorkingMemoryTest.evictionIsFifoAtCapacity` passed
- `BoundedWorkingMemoryTest.queryCaseInsensitiveScoresLower` passed
- `BoundedWorkingMemoryTest.queryForOtherKindReturnsEmptyAndNoConsultation`
  passed
- `MemoryTypesTest` (S4.0) still passes

If CI fails twice on the same root cause, stop and report the log.

---

**End of S4.1 session package.**