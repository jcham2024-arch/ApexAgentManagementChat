# BUILD-SPEC: Spring Boot Starter (`apex-spring-boot-starter`) — Part 2 of 2

**Source:** docs/specs/spring-boot-starter-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 2 of 2
**Cross-reference:** See `spring-boot-starter-spec-part-1.md` for sections 1-6.

## 7. Determinism Impact

**No impact on Tier 0 determinism.** The starter is Tier 2 and does not
participate in plan generation. `PlanLoop` behavior is identical whether
constructed manually or via the Spring bean — the same Tier 0 code path
executes.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **Bean exposure** | Kernel ports exposed as Spring beans; application is |
| | responsible for securing bean access (Spring Security, method |
| | security, etc.). |
| **Auth filter** | JWT verification delegated to `AuthFilter` bean; starter |
| | does not implement auth logic. |
| **Secrets** | `SecretsPort` bean provides credential access; secret storage |
| | is the deployment's responsibility (env vars, Vault, etc.). |

## 9. Audit Impact

The starter does not write to the audit chain directly. Audit events flow
through `AuditPort` (bean) and are recorded by whatever `AuditPort`
implementation is active (default `InMemoryAuditLog` or the durable
`SqliteAuditLog` adapter).

## 10. Failure, Cancellation, and Timeout Semantics

| Scenario | Required Behavior |
| :--- | :--- |
| **Bean missing and no default** | Context fails to start with a clear |
| | `NoSuchBeanDefinitionException`. |
| **PlanLoop start failure** | `SmartLifecycle.start()` throws; context |
| | initialization fails fast. |
| **PlanLoop stop** | `stop()` cancels the `CancellationToken` |
| | and waits for in-flight steps to drain. |
| **Context shutdown** | Shutdown hook triggers `PlanLoop.stop()`. |
| **Caller-supplied bean fails** | Spring context fails to start; the caller's |
| | bean is responsible for its own startup errors. |

Cancellation tokens are not Spring types; they are plain Java objects
passed from the starter to `PlanLoop`.

## 11. Test Plan

### 11.1 Unit tests

- Auto-configuration class is `@Configuration` and scans correctly.
- Each `@Bean` method is annotated `@ConditionalOnMissingBean`.
- `@ConditionalOnMissingBean` appears only on auto-configuration |
| classes, never on regular `@Configuration` classes.
- `PlanLoop` bean implements `SmartLifecycle`.
- `@ConfigurationProperties` classes bind grouped properties correctly.

### 11.2 Integration tests

- **Default beans:** A test application context with no custom beans |
| observes all default port beans created by the starter.
- **Caller-supplied bean:** A test application that supplies its own |
| `AuditPort` bean observes that the starter backs off and creates no |
| duplicate `AuditPort` bean (acceptance test minimum).
- **PlanLoop lifecycle:** `start()` begins the loop; `stop()` cancels |
| the token and drains in-flight steps; context shutdown triggers |
| `stop()`.
- **No Spring in Tier 0:** `CorePurityTest` passes in all Tier 0 |
| modules; no Spring imports in `engine-core`, `engine-planner`, |
| `engine-tools`, `engine-store`, `engine-json`, or `engine-memory`.

### 11.3 Architectural enforcement

- `CorePurityTest` passes in all Tier 0 modules.
- The starter's `pom.xml` does not introduce any dependency that |
| violates the Tier 1 dependency policy.

### 11.4 Coverage targets

- All `@Bean` methods exercised (default and caller-supplied override).
- `SmartLifecycle` start/stop exercised.
- `@ConfigurationProperties` binding exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. `apex-spring-boot-starter` builds under `mvn -B -f |
| project/apex/pom.xml verify`.
2. `@ConditionalOnMissingBean` used only on auto-configuration classes.
3. No Spring type appears in `engine-core`, `engine-planner`, |
| `engine-tools`, `engine-store`, `engine-json`, or `engine-memory`.
4. `PlanLoop` bean implements `SmartLifecycle` with explicit start/stop.
5. A test application supplying its own `AuditPort` bean observes no |
| duplicate `AuditPort` bean created by the starter (acceptance test |
| minimum).
6. A test application supplying no beans observes all default port |
| beans (acceptance test minimum).
7. Context shutdown hook stops `PlanLoop` cleanly.
8. `CorePurityTest` passes in all Tier 0 modules.
9. `@ConfigurationProperties` classes grouped (not scattered |
| `@Value`).

## 13. Rollback / Disable Strategy

- **Exclude the starter:** Applications that do not want Spring Boot |
| auto-configuration simply do not add |
| `apex-spring-boot-starter` as a dependency.
- **Exclude individual beans:** Callers supply their own bean for any |
| port they want to manage manually; `@ConditionalOnMissingBean` skips |
| the auto-configured default.
- **No Tier 0 impact:** Removing the starter has zero effect on Tier 0 |
| modules.

## 14. Out of Scope (Explicit)

- Spring AI auto-configuration (handled by the Spring AI adapter spec, |
| gated by `@ConditionalOnClass` on `spring-ai` being present).
- MCP adapter auto-configuration (handled by the MCP adapter spec).
- JWT verification logic (delegated to `AuthFilter` bean from |
| `apex-auth`).
- Persistent audit storage (delegated to `AuditPort` bean; default is |
| `InMemoryAuditLog`, durable option is |
| `apex-audit-persistence-sqlite`).

## 15. Governance References

| Artifact | Path |
| :--- | :--- |
| Spike finding | `docs/spikes/A3-spring-boot-starter.md` |
| Governing ADR | `docs/adr/ADR-003-spring-integration-boundary.md` |
| Decision note | `docs/contracts/decisions/Q7-Q9-spring-starter.md` |
| Port contract inventory | `docs/contracts/ports.md` (all Tier 0 |
| | port rows) |
| NFR capture | `docs/contracts/nfr.md` §3 |
| Open questions | `docs/contracts/open-questions.md` (Q7, Q9 |
| | closed) |
| Spec readiness checklist | `docs/specs/README.md` |

**Decision governance:** Q7/Q9 (starter coordinates |
| `com.apex:apex-spring-boot-starter`, |
| `@ConditionalOnMissingBean` on auto-config only, Tier 0 isolation, |
| OpenAI-compatible provider default) are closed and linked above. |
| These decisions constrain this spec as stated in each decision |
| note's "Constraint on future specs" section.
