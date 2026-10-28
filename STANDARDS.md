# Supplements — Coding Standards

Loaded from P1 onward and stays loaded for every subsequent phase.

## Java 21

- **Records** for all value objects. Compact constructor must
  defensive-copy every collection component: `List.copyOf`,
    `Map.copyOf`, `Set.copyOf`. Arrays must be `.clone()`d.
    - **Sealed interfaces** for all discriminated unions. Every subtype
      listed in `permits`. A `switch` over the sealed type with no `default`
        must compile — this is exhaustiveness, and it is tested.
        - **No `ScopedValue`.** Preview in Java 21 LTS. Thread context explicitly
          via method parameters.
          - **No `ThreadLocal` in the agent loop.** One `ThreadLocal` read at the
            HTTP filter boundary is allowed; it must be copied into
              `ExecutionContext` and threaded from there.
              - **Virtual threads** for all I/O:
                `Executors.newVirtualThreadPerTaskExecutor()`. Never a fixed thread
                  pool for agent reasoning.
                  - **No `var`** in public method signatures or record components. `var`
                    is fine for locals where the type is obvious from the RHS.
                    - **Javadoc** on every public class and method. Explain *why*, not *what*.
                      Invariant references use the format `{@link ...}` + `K<n>` / `E<n>` /
                        `C<n>` ID.
                        - **Immutability is a promise.** A record whose compact constructor
                          doesn't defensively copy is a bug, not a style preference.

                          ## TypeScript 5.x

                          - **Zero `any`.** `unknown` + Zod parse at every boundary.
                          - **Generated types** live in `src/generated/`. Never hand-edit. Never
                            import a type that isn't from there or from a local Zod schema.
                            - **Components** are function components with explicit
                              `React.FC<Props>`. Props are `readonly`.
                              - **No `useEffect`** for data fetching. Use the typed SSE client.
                              - **Zod at the boundary.** Every response, every file import, every
                                user input goes through a Zod schema before entering application
                                  state.

                                  ## Tests

                                  - **Java:** JUnit 5 + AssertJ + jqwik (property tests) +
                                    Testcontainers (PostgreSQL integration). Every invariant in
                                      `docs/INVARIANTS.md` gets a corresponding test.
                                      - **TypeScript:** Vitest. Compactor fixtures, SSE reconnect, Zod
                                        boundary rejection.
                                        - **Test naming:** `<Unit>Test` for unit, `<Unit>IT` for integration.
                                          Integration tests use `@Tag("integration")`.
                                          - **If the test doesn't exist, the invariant doesn't exist.** A claim
                                            without a test is marketing.

                                            ## Package Naming

                                            - Base package: `com.agentplatform.<module>`
                                            - Modules: `kernel.core`, `kernel.rules`, `kernel.agent`,
                                              `evolution.cas`, `evolution.sentinel`, `plugin.mcp`, `plugin.process`,
                                                `ipc.osc`, `ipc.engine`, `automation`
                                                - Sub-packages within a module use singular domain nouns:
                                                  `id/`, `security/`, `context/`, `concurrent/`, `message/`, `stream/`
                                                  - Test packages mirror main packages exactly.

                                                  ## Logging

                                                  - SLF4J façade only. Never `System.out`.
                                                  - No logging in `domain/` packages. Log at the application or
                                                    infrastructure boundary.
                                                    - Never log secrets, tokens, prompts containing user PII, or raw tool
                                                      argv (may contain credentials).

                                                      ## Dependency Hygiene

                                                      - Only add dependencies listed in `docs/PHASES.md §<phase>`
                                                        "Dependencies to add".
                                                        - Never add a dependency in phase N that is scheduled for phase N+k.
                                                        - Every added dependency must appear in the parent `pom.xml` with an
                                                          explicit version (no version ranges).
                                                          ```
                                                          