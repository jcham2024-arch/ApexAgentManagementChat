# PHASE-S2.4-MODEL-ADAPTER-CRITERIA.md — Session Package, Part 8 of 8

**Version:** 1.0 · **Sub-phase of S2** · **Part 8 of 8:** §7–§10, INVARIANTS K6, exit criteria, commits.
Parts: SESSION · CORE-PORT · LLM-BASICS · CONFIG · PROTOCOL · PROVIDER ·
METADATA-TESTS · CRITERIA, each `PHASE-S2.4-MODEL-ADAPTER-<part>.md`
(`AGENT_RULES.md` §2.13).

---
## 7. Update `docs/INVARIANTS.md`

Update the K6 row:

| ID | Claim | Test |
|---|---|---|
| K6 | Cooperative cancellation ≤100ms | `PlanLoopTest.cancellation` (S2.2) · `OpenAiCompatibleProviderStreamTest.cancellation` (S2.4) |

## 8. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `ChatModelPort` has the default `stream()` method; existing
      `PortTest` lambda implementations still compile.
- [ ] `ChatChunk` exists in `engine-core.port.model`.
- [ ] `OpenAiCompatibleProvider` implements both `chat` and `stream`.
- [ ] `ProviderConfigLoader.fromMap` covers all five provider paths and
      the error path.
- [ ] All test classes pass; the mock-server tests use
      `com.sun.net.httpserver.HttpServer` on ephemeral ports.
- [ ] `docs/INVARIANTS.md` K6 row updated.
- [ ] No enterprise dependency added.
- [ ] No file exceeds 200 lines.
- [ ] `engine-tools-impl` and `engine-planner` are untouched.
- [ ] Both `Json` (S2.3) and `JsonParser` (S2.4) carry a comment
      cross-referencing each other.

## 9. Do NOT add

- Any Spring, Google, Embabel, Jackson, Micrometer dependency
- Any change to `engine-tools-impl` or `engine-planner`
- Any Layer 1 spec type (`StreamingEvent`, `Message`, etc.)
- Any tool, storage, or CLI code
- Any retry logic — 429 handling is the caller's job in S2.4; retry
      policy lands in S10
- Any live network call from tests — mock server only
- Any file not listed in §4 or §6

## 10. Commit

Four commits on `phase/s2.4-model-adapter`:

1. `feat(S2.4): add ChatChunk and default stream method to ChatModelPort`
2. `feat(S2.4): add provider config, JSON parser, SSE parser, OpenAI protocol helpers`
3. `feat(S2.4): add OpenAiCompatibleProvider implementing chat and stream`
4. `test(S2.4): add provider, config, parser, and stream tests; update INVARIANTS K6`

Push. Report CI link, per-test-class pass counts, and confirmation
that the streaming cancellation test passed. If CI fails twice on the
same root cause, stop and report the log.

---

**End of S2.4 session package.**
