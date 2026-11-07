# PHASE-S2.3-TOOLS-CONTRACTS-CRITERIA.md — Session Package, Part 8 of 8

**Version:** 1.0 · **Sub-phase of S2** · **Part 8 of 8:** §5.4–§9, contract tests, INVARIANTS, exit criteria, commits.
Parts: SESSION · JSON · JAIL · FILE-TOOLS · RUN-COMMAND · HTTP-EXECUTOR ·
METADATA-TESTS · CONTRACTS-CRITERIA, each `PHASE-S2.3-TOOLS-<part>.md`.
Cut for the 200-line rule in `AGENT_RULES.md` §2.13.

---
The K5 test proves:
- The tool kills the process tree on timeout (script's two `sleep 287` grandchildren are gone).
- `countBashWithScript` before == after (no leaked processes from this test).

### 5.4 Other tests — contracts

Follow the same pattern for:

- **`ReadFileToolTest`** — reads a file, rejects a directory, rejects a
  file over 50 KB, rejects a path outside the jail.
- **`WriteFileToolTest`** — writes a file, overwrites, rejects path
  whose parent does not exist, rejects path outside the jail.
- **`ListFilesToolTest`** — lists files, filters by glob, rejects a
  non-directory, rejects path outside the jail.
- **`HttpGetToolTest`** — uses `com.sun.net.httpserver.HttpServer` on
  port 0 to serve `hello` on `/`. Test: 200 returns body, 404 throws
  ToolException. Second test: `http://169.254.169.254/` throws
  "forbidden address".
- **`KernelToolExecutorTest`** — unknown tool name returns error
  `ToolResult`, malformed `argumentsJson` returns error `ToolResult`,
  valid `read_file` call returns success `ToolResult` with content.

All test classes ≤ 200 lines. Split if needed.

## 6. Update `docs/INVARIANTS.md`

Replace the K2 and K5 rows:

| ID | Claim | Test |
|---|---|---|
| K2 | No shell interpolation | `RunCommandToolTest.doesNotInterpretShellMetacharacters_K2` |
| K5 | Kill process tree via ProcessHandle.descendants | `RunCommandToolTest.enforcesTimeoutAndKillsTree_K5` |

Keep all other rows unchanged.

## 7. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `JsonTest` covers all listed cases.
- [ ] `RunCommandToolTest` includes `doesNotInterpretShellMetacharacters_K2`
      and `enforcesTimeoutAndKillsTree_K5`, both passing.
- [ ] All ArchUnit Tier 0 gates still pass (no new enterprise imports
      reached Tier 0).
- [ ] `jdeps` gate still passes on Tier 0 jars.
- [ ] No file over 200 lines.
- [ ] No enterprise dependency added.
- [ ] `docs/INVARIANTS.md` K2 and K5 rows updated.
- [ ] `engine-tools` (Tier 0) is untouched.

## 8. Do NOT add

- Any code in `engine-tools` (Tier 0) — stays empty
- Any port implementation other than `ToolExecutorPort` (via
  `KernelToolExecutor`)
- Any JSON library, HTTP library, or process library — JDK only
- Any dependency change to `engine-tools-impl/pom.xml`
- Any tool beyond the five named above
- Any spec file
- Any file not listed in §4 or §5

## 9. Commit

Three commits on `phase/s2.3-tools`:

1. `feat(S2.3): add Json parser, RealPathJail, ProcessTreeKiller`
2. `feat(S2.3): add five kernel tools and KernelToolExecutor`
3. `test(S2.3): add tool tests including K2 and K5 proofs, update INVARIANTS`

Push. Report CI link, per-test-class pass counts, and confirmation that
`doesNotInterpretShellMetacharacters_K2` and
`enforcesTimeoutAndKillsTree_K5` both passed. If CI fails twice on the
same root cause, stop and report the log.

---

**End of S2.3 session package.**
