# Supplements — Salvage Manifest

Loaded **only** when the §2 load matrix in `AGENT_RULES.md` lists it for
your current phase. This file tells you which prior-repo components to
read and how to adapt them.

**Before adapting any component:** open the source file in the
`LeanJavallm` or `Agenticplatformsetup` workspace. If the class name, its
public interface, or its behavior differs from what this file says, **stop
and report** (AGENT_RULES.md §8.2). The reference repos may have drifted.

---

## From `LeanJavallm`

Base package: `com.leanllm.*`. Zero external dependencies. Apex is Spring
Boot + LangChain4j — you cannot copy; you reimplement.

### `CancellationToken` — loaded for **P1**

- **Source:** `com.leanllm.concurrency.CancellationToken`
- **Target:** `kernel/kernel-core/.../concurrent/CancellationToken.java`
- **Keep:** `AtomicBoolean` state, `cancel()` idempotent, `isCancelled()`.
- **Add:** `CompletableFuture<Void> onCancel()` (complete exactly once on
  first `cancel()`), `void throwIfCancelled()` (throws
    `CancellationException`), `boolean cancel()` returns true on first call.
    - **Test:** a `CompletableFuture.anyOf(cancelFuture, pollFuture)` fixture
      proves cancellation is observed within 100 ms.
      - **Do not:** bring in scheduling, executors, or threads. The primitive is
        a flag plus a future.

        ### `ProcessTreeTerminator` — loaded for **P4**

        - **Source:** `com.leanllm.concurrency.ProcessTreeTerminator`
        - **Target:** `plugin-host/plugin-process/.../ProcessTreeTerminator.java`
        - **Keep:** `ProcessHandle.descendants()` → `forEach(destroyForcibly)`.
        - **Add:** accepts a `CancellationToken`; registers the terminator on
          `token.onCancel()`; returns after all descendants report `!isAlive()`
            with a bounded wait (2 s).
            - **Test:** `OrphanProcessIT` spawns a grandchild, cancels, asserts zero
              surviving processes via `ProcessHandle.allProcesses()`.

              ### `TokenEstimator` — loaded for **P6**

              - **Source:** `com.leanllm.agent.TokenEstimator`
              - **Target:** `kernel/kernel-rules/.../token/TokenEstimator.java`
              - **Design:** define an **interface** `TokenEstimator` with
                `int estimate(String text)`. Provide `HeuristicEstimator` (chars/4
                  approximation) as the default. A real tokenizer can be dropped in later
                    without touching call sites.
                    - **Do not:** hardcode a specific model's tokenizer.

                    ### `ContextCompactor` — loaded for **P6**

                    - **Source:** `com.leanllm.agent.ContextCompactor`
                    - **Target:** `kernel/kernel-agent/.../context/ContextCompactor.java`
                    - **Keep:** sliding window that prunes oldest conversational turns when
                      the estimated token count exceeds the budget.
                      - **Add (invariant C4):** preserve the `SystemMessage` at index 0
                        unconditionally; when a tool-call message is preserved, its paired
                          `ToolResultMessage` is preserved regardless of window position. Never
                            split a pair.
                            - **Test:** `CompactorFixtureTest` forces overflow and asserts SYSTEM
                              intact, tool pairs intact, token count under budget.

                              ### `AgentEngine` — loaded for **P6** (reference only)

                              - **Source:** `com.leanllm.agent.AgentEngine`
                              - **Target:** none. Reference the loop **shape** when writing
                                `DynamicAgentFactory` + `SupervisorAgent` in P6.
                                - **Loop shape to replicate:** iterate → check cancel → call model →
                                  if no tool calls, return content → else dispatch each tool, append
                                    results, continue. Cap iterations.
                                    - **Do not copy:** the hand-rolled `LlmClient`, `InferenceRequest`,
                                      `InferenceResponse` types. Use LangChain4j's `ChatLanguageModel` and
                                        `ChatMemory`.

                                        ---

                                        ## From `Agenticplatformsetup`

                                        **Important:** the Java files its README describes **do not exist in the
                                        repo**. Only patterns are salvageable. Do not spend time searching for
                                        `McpPathPolicyFilter.java`, `EpisodicMemoryGatekeeper.java`, or
                                        `GeminiNativeClient.java` — they are not there.

                                        ### Realpath Jail pattern — loaded for **P4**

                                        - **Source:** README description only (no code).
                                        - **Target:** `plugin-host/plugin-process/.../RealPathJail.java`
                                        - **Pattern:** given a workspace root and a candidate path, resolve both
                                          via `Path.toRealPath()` (which canonicalizes `..` and follows symlinks),
                                            then assert `candidateResolved.startsWith(rootResolved)`. Reject with
                                              `SecurityException` on any exception (missing path, permission denial,
                                                deviation). Never fall back to string comparison.
                                                - **Test:** `PathTraversalIT` — `../`, symlink to `/tmp`, absolute path
                                                  outside root, and a path whose parent is a symlink outside root, all
                                                    rejected.

                                                    ### Tiered `SKILL.md` line limits — loaded for **P5**

                                                    - **Source:** README description only (no code).
                                                    - **Target:** `evolution-sentinel/.../BudgetValidator.java`
                                                    - **Pattern:** `SKILL.md` files that declare `permissions` in their YAML
                                                      front-matter have a 150-line limit; instruction-only skills (no
                                                        `permissions` key) have a 200-line limit. Exceeding the limit rejects
                                                          the commit with `SANDBOX_REJECTED:BUDGET`.

                                                          ### Descending AST patch sort — **do not load for v1**

                                                          - Deferred to post-v1. Not required for any phase. Do not implement.

                                                          ---

                                                          ## Adaptation Rules (apply to every salvage)

                                                          1. **Read source first.** If the file is missing or its interface differs
                                                             from this document, stop (§8.2).
                                                             2. **Reimplement, don't copy.** Match Apex package layout, Apex naming,
                                                                Apex invariants. The source is a *reference*, not a template.
                                                                3. **Carry the tests.** Every salvage component has an Apex test in
                                                                   `docs/INVARIANTS.md`. If the source has a test you can adapt, adapt
                                                                      it. If not, write one.
                                                                      4. **Note the salvage in the commit body:** `Refs: P<n> (salvage:
                                                                         <component>)`.
                                                                         5. **Do not pull in the source's dependencies.** LeanJavallm is
                                                                            zero-dependency; Apex is Spring Boot. If a source file uses a class
                                                                               from `java.util` only, great. If it uses anything else, reimplement.
                                                                               ```
                                                                               