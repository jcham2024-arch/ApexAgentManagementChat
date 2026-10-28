# Apex Agent Management Chat

> Enterprise-grade, self-evolving multi-agent platform. Java 21 microkernel
> + Content-Addressable Storage evolution plane + Tauri 2.0 universal client.

## What this is

Apex is a **dual-plane agentic platform**:

- **Kernel Plane** — an immutable, compile-time-typed Java 21 microkernel
  (Spring Boot 3.3, LangChain4j, Virtual Threads) that owns execution
    invariants, tenant isolation, guardrails, and the LLM reasoning loop.
      It has **no write path** to its own behavior.
      - **Evolution Plane** — a Content-Addressable Storage (CAS) Git-tree where
        all agent growth (prompts, skills, tool manifests, MCP configs) lives as
          versioned blobs on ephemeral sandbox branches. Changes are gated by an
            automated validation sentinel and human sign-off before merging to `main`.

            The frontend is a **Tauri 2.0 + React 19** universal client with a
            Perchance-inspired workspace layout, connected via typed SSE over a
            strictly isomorphic TypeScript/Java contract.

            ## Why this design

            The central problem with autonomous AI agents is: *how do you let them
            improve themselves without letting them destroy the host?* Apex answers
            this by making "self-improvement" a first-class, versioned, sandboxed,
            human-auditable operation — the same discipline Git + CI/CD brought to
            software delivery.

            The distinguishing feature against prior attempts is **enforced
            isomorphism**: the TypeScript client cannot drift from the Java kernel
            because the CI build fails when generated types diverge (see
            `docs/INVARIANTS.md` §C1).

            ## Repository layout

            ```

            .
            ├── kernel/                    # Java 21 microkernel (Spring Boot 3.3)
            │   ├── kernel-core/           # Domain records, sealed hierarchies, cancellable context
            │   ├── kernel-agent/          # LangChain4j ReAct orchestration, context compaction
            │   └── kernel-rules/          # Guardrails, RBAC, CIDR validation, token budgets
            ├── evolution/                 # CAS workspace engine
            │   ├── evolution-cas/         # Blob/tree/commit/branch storage
            │   └── evolution-sentinel/    # SandboxValidationSentinel (6 categories)
            ├── plugin-host/               # MCP + ProcessBuilder sandbox
            │   ├── plugin-mcp/            # McpToolProvider wiring
            │   └── plugin-process/        # SandboxedProcessExecutor, ProcessTreeTerminator
            ├── ipc-omnibus/               # OSC / Godot / Blender / HTTP adapters
            ├── os-automation/             # Accessibility tree + vision fallback
            ├── client/                    # Tauri 2.0 + React 19 + TS 5.x
            │   ├── src/                   # React components
            │   ├── src-tauri/             # Rust shell
            │   └── src/generated/         # openapi-typescript output (CI-enforced)
            ├── docs/
            │   ├── ROADMAP.md             # Phased delivery plan
            │   └── INVARIANTS.md          # Testable safety guarantees
            └── .github/workflows/         # CI: type-drift, tests, security

            ```

            ## Provenance

            Apex consolidates and corrects two earlier explorations:

            **`jcham2024-arch/LeanJavallm`** — a zero-dependency Java LLM engine. Real,
            working code. Apex adapts the following components:

            - `ProcessTreeTerminator` → descendant-walk kill for sandboxed processes
            - `ContextCompactor` + `TokenEstimator` → context-overflow defense and
              token accounting foundation
              - `CancellationToken` → cooperative cancellation primitive for SSE and
                tool execution
                - `AgentEngine` loop structure → reference for the LangChain4j supervisor
                  wiring (the hand-rolled `LlmClient` is replaced by `ChatModelProvider`)

                  **`jcham2024-arch/Agenticplatformsetup`** — an aspirational spec whose
                  Java claims are not backed by files. Apex retains only the following
                  patterns:

                  - Realpath jail (canonical-path `startsWith(root)` filter) → path traversal
                    invariant in `SandboxedProcessExecutor`
                    - Descending-order AST patch sorting → noted for future tooling, not
                      implemented in v1
                      - Tiered `SKILL.md` token budgets (150/200 lines) → adopted as a concrete
                        rule in the sentinel's budget category

                        Everything else — CAS engine, sentinel, MCP wiring, accessibility layer,
                        Tauri client, type bridge — is built fresh. No code is copied; the salvage
                        list is design-level only, except for `LeanJavallm` components that will
                        be re-implemented against Apex interfaces (not copy-pasted, to preserve
                        package boundaries and immutability invariants).

                        ## Status

                        **Pre-alpha.** Design consolidated; implementation begins at Phase 1
                        (see `docs/ROADMAP.md`).

                        ## License

                        TBD.
                        ```
                        