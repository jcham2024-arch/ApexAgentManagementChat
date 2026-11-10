# BUILD-SPEC: Spring Boot Starter (`apex-spring-boot-starter`)

## 1. Module Identity

| Field | Value |
| :--- | :--- |
| **Module name** | `apex-spring-boot-starter` |
| **Repository location** | `project/apex-platform/apex-spring-boot-starter/` |
| **Parent aggregator** | `project/apex-platform/pom.xml` |
| **Tier** | **Tier 2** (platform adapter — Spring allowed here, never in Tier 0) |
| **Governed by** | ADR-003 (Spring integration boundary) |
| **Spike input** | `docs/spikes/A3-spring-boot-starter.md` |
| **Decision input** | `docs/contracts/decisions/Q7-Q9-spring-starter.md` |
| **Spec readiness** | **READY** — Q7/Q9 closed, ADR-003 accepted, port classification unchanged |

## 2. Purpose

Provide a Spring Boot auto-configuration starter that lets an existing
Spring application embed the Apex kernel with zero Spring types in Tier 0.
The starter exposes kernel ports (`PlanLoop`, `AuditPort`, `ChatModelPort`,
`ToolExecutorPort`, etc.) as Spring beans with sensible defaults, each
overridable by a caller-supplied bean via `@ConditionalOnMissingBean`.

## 3. Ports Consumed and Provided

### Consumed (Tier 0 contracts)

The starter depends on Tier 0 modules (`engine-core`, `engine-planner`,
`engine-tools`, `engine-store`, `engine-json`, `engine-memory`) and
consumes their ports through the public API surface of each port
interface:

| Port | Package | Used as |
|------|---------|---------|
| `PlanLoop` | `com.apex.engine.core.loop` | Spring bean, `SmartLifecycle` |
| `AuditPort` | `com.apex.engine.core.port` | Spring bean, `@ConditionalOnMissingBean` |
| `ChatModelPort` | `com.apex.engine.core.port` | Spring bean, `@ConditionalOnMissingBean` |
| `ToolExecutorPort` | `com.apex.engine.core.port` | Spring bean, `@ConditionalOnMissingBean` |
| `StoragePort` | `com.apex.engine.core.port` | Spring bean, `@ConditionalOnMissingBean` |
| `CheckpointPort` | `com.apex.engine.core.port` | Spring bean, `@ConditionalOnMissingBean` |
| `RuntimePolicy` | `com.apex.engine.core.port` | Spring bean, `@ConditionalOnMissingBean` |
| `GatePlacementPolicy` | `com.apex.engine.core.port` | Spring bean, `@ConditionalOnMissingBean` |
| `SecretsPort` | `com.apex.engine.core.port` | Spring bean, `@ConditionalOnMissingBean` |
| `MetricsPort` | `com.apex.engine.core.port` | Spring bean, `@ConditionalOnMissingBean` |
| `TelemetryPort` | `com.apex.engine.core.port` | Spring bean, `@ConditionalOnMissingBean` |

### Provided (Tier 2 starter beans)

| Bean | Type | Auto-configured? | Overridable? |
|------|------|-----------------|--------------|
| `planLoop` | `PlanLoop` | Yes (`@ConditionalOnMissingBean`) | Yes |
| `auditPort` | `AuditPort` | Yes (`@ConditionalOnMissingBean`) | Yes |
| `chatModelPort` | `ChatModelPort` | Yes (`@ConditionalOnMissingBean`) | Yes |
| `toolExecutorPort` | `ToolExecutorPort` | Yes (`@ConditionalOnMissingBean`) | Yes |
| `storagePort` | `StoragePort` | Yes (`@ConditionalOnMissingBean`) | Yes |
| `checkpointPort` | `CheckpointPort` | Yes (`@ConditionalOnMissingBean`) | Yes |
| `runtimePolicy` | `RuntimePolicy` | Yes (`@ConditionalOnMissingBean`) | Yes |
| `gatePlacementPolicy` | `GatePlacementPolicy` | Yes (`@ConditionalOnMissingBean`) | Yes |
| `secretsPort` | `SecretsPort` | Yes (`@ConditionalOnMissingBean`) | Yes |
| `metricsPort` | `MetricsPort` | Yes (`@ConditionalOnMissingBean`) | Yes |
| `telemetryPort` | `TelemetryPort` | Yes (`@ConditionalOnMissingBean`) | Yes |

### Port classification

- **All Tier 0 ports**: **unchanged** — no method signatures or signatures
  altered. The starter only *consumes* ports as beans; it does not modify
  any port interface.
- **No new Tier 0 ports introduced.**

## 4. Public API Surface and Compatibility

### Java API

The starter exposes a single `@Configuration` class:

```java
@Configuration
public class ApexAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public PlanLoop planLoop(...) { ... }

    @Bean
    @ConditionalOnMissingBean
    public AuditPort auditPort(...) { ... }

    // ... one @Bean per port, each @ConditionalOnMissingBean
}
```

And `@ConfigurationProperties` classes for grouped configuration:

```java
@ConfigurationProperties(prefix = "apex.audit")
public class AuditProperties { ... }

@ConfigurationProperties(prefix = "apex.plan-loop")
public class PlanLoopProperties { ... }
```

### Compatibility statement

- **Tier 0 port interfaces**: unchanged — the starter consumes them as-is.
- **No breaking changes** to any Tier 0 module.
- **New module only** — no existing module is modified.

## 5. Dependency Ceiling and Justification

| Dependency | Scope | Justification |
| :--- | :--- | :--- |
| `org.springframework.boot:spring-boot-starter` | Compile | Spring Boot auto-configuration foundation (Tier 2 allowed). |
| `org.springframework.boot:spring-boot` | Compile | `@Configuration`, `@ConditionalOnMissingBean`, `@ConfigurationProperties` (Tier 2 allowed). |
| `com.apex:engine-core` | Compile | Tier 0 ports and kernel (project dependency). |
| `com.apex:engine-planner` | Compile | `AStarPlanner`, `Blackboard`, `Goal` (project dependency). |
| `com.apex:engine-tools` | Compile | Tool metadata and policy records (project dependency). |
| `com.apex:engine-store` | Compile | `StoragePort`, `CheckpointPort` interfaces (project dependency). |
| `com.apex:engine-json` | Compile | Minimal JSON parser (project dependency). |
| `com.apex:engine-memory` | Compile | Memory, compactor, router (project dependency). |
| `org.jspecify` | Compile | Null-safety annotations per project standards. |
| `org.junit.jupiter:junit-jupiter` | Test | Test framework per project standard. |
| `org.assertj:assertj-core` | Test | Assertions per project standard. |
| `com.tngtech.archunit:archunit-junit5` | Test | Architectural enforcement per project standard. |

**Tier 0 purity:** Zero Spring types in `engine-core`, `engine-planner`,
`engine-tools`, `engine-store`, `engine-json`, or `engine-memory`. The
starter depends on Tier 0; Tier 0 never depends on the starter. `CorePurityTest`
must pass for all Tier 0 modules.

## 6. Implementation Design

### 6.1 Auto-configuration class

A single `@Configuration` class (`ApexAutoConfiguration`) provides one
`@Bean` per port, each annotated `@ConditionalOnMissingBean`. When a caller
supplies their own bean for a port, Spring skips the auto-configured default.
`@ConditionalOnClass` and `@ConditionalOnProperty` gate optional
integrations (e.g., Spring AI adapter only when `spring-ai` is on the
classpath).

### 6.2 Conditional scope rule

`@ConditionalOnMissingBean` is used **only on auto-configuration classes**,
never on regular `@Configuration` classes, per Spring's own documentation
(evaluation timing depends on bean registration order).

### 6.3 PlanLoop lifecycle

`PlanLoop` is exposed as a Spring bean implementing Spring's
`SmartLifecycle`:
- `start()` begins the loop with a `CancellationToken`.
- `stop()` cancels the token and drains in-flight steps.
- The context shutdown hook triggers `stop()`.

Cancellation is exposed as a bean method or application event — **not** as
a Spring-specific type leaking into the core.

### 6.4 Auth integration

Auth integrates as a Spring `OncePerRequestFilter` (or `HandlerInterceptor`):
extracts the JWT from the `Authorization` header, verifies via the
`TokenVerifier`/`AuthFilter` bean, and places the `Principal`/`AgentIdentity`
in a request-scoped `ExecutionContext` copied at the HTTP filter boundary
(one ThreadLocal read allowed, per AGENT_RULES §2.5).

### 6.5 Configuration surface

Grouped `@ConfigurationProperties` classes (`apex.audit`, `apex.plan-loop`,
etc.) rather than dozens of scattered `@Value` injections.

## 7. Determinism Impact

**No impact on Tier 0 determinism.** The starter is Tier 2 and does not
participate in plan generation. `PlanLoop` behavior is identical whether
constructed manually or via the Spring bean — the same Tier 0 code path
executes.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **Bean exposure** | Kernel ports exposed as Spring beans; application is responsible |
| | for securing bean access (Spring Security, method security, etc.). |
| **Auth filter** | JWT verification delegated to `AuthFilter` bean; starter does |
| | not implement auth logic. |
| **Secrets** | `SecretsPort` bean provides credential access; secret storage is the |
| | deployment's responsibility (env vars, Vault, etc.). |

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

Cancellation tokens are not Spring types; they are plain Java objects passed
from the starter to `PlanLoop`.

## 11. Test Plan

### 11.1 Unit tests

- Auto-configuration class is `@Configuration` and scans correctly.
- Each `@Bean` method is annotated `@ConditionalOnMissingBean`.
- `@ConditionalOnMissingBean` appears only on auto-configuration classes,
  never on regular `@Configuration` classes.
- `PlanLoop` bean implements `SmartLifecycle`.
- `@ConfigurationProperties` classes bind grouped properties correctly.

### 11.2 Integration tests

- **Default beans:** A test application context with no custom beans
  observes all default port beans created by the starter.
- **Caller-supplied bean:** A test application that supplies its own
  `AuditPort` bean observes that the starter backs off and creates no
  duplicate `AuditPort` bean (acceptance test minimum).
- **PlanLoop lifecycle:** `start()` begins the loop; `stop()` cancels the
  token and drains in-flight steps; context shutdown triggers `stop()`.
- **No Spring in Tier 0:** `CorePurityTest` passes in all Tier 0 modules;
  no Spring imports in `engine-core`, `engine-planner`, `engine-tools`,
  `engine-store`, `engine-json`, or `engine-memory`.

### 11.3 Architectural enforcement

- `CorePurityTest` passes in all Tier 0 modules.
- The starter's `pom.xml` does not introduce any dependency that violates
  the Tier 1 dependency policy.

### 11.4 Coverage targets

- All `@Bean` methods exercised (default and caller-supplied override).
- `SmartLifecycle` start/stop exercised.
- `@ConfigurationProperties` binding exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. `apex-spring-boot-starter` builds under `mvn -B -f project/apex/pom.xml verify`.
2. `@ConditionalOnMissingBean` used only on auto-configuration classes.
3. No Spring type appears in `engine-core`, `engine-planner`, `engine-tools`,
   `engine-store`, `engine-json`, or `engine-memory`.
4. `PlanLoop` bean implements `SmartLifecycle` with explicit start/stop.
5. A test application supplying its own `AuditPort` bean observes no
   duplicate `AuditPort` bean created by the starter (acceptance test
   minimum).
6. A test application supplying no beans observes all default port beans
   (acceptance test minimum).
7. Context shutdown hook stops `PlanLoop` cleanly.
8. `CorePurityTest` passes in all Tier 0 modules.
9. `@ConfigurationProperties` classes grouped (not scattered `@Value`).

## 13. Rollback / Disable Strategy

- **Exclude the starter:** Applications that do not want Spring Boot auto-
  configuration simply do not add `apex-spring-boot-starter` as a
  dependency.
- **Exclude individual beans:** Callers supply their own bean for any port
  they want to manage manually; `@ConditionalOnMissingBean` skips the
  auto-configured default.
- **No Tier 0 impact:** Removing the starter has zero effect on Tier 0
  modules.

## 14. Out of Scope (Explicit)

- Spring AI auto-configuration (handled by the Spring AI adapter spec,
  gated by `@ConditionalOnClass` on `spring-ai` being present).
- MCP adapter auto-configuration (handled by the MCP adapter spec).
- JWT verification logic (delegated to `AuthFilter` bean from `apex-auth`).
- Persistent audit storage (delegated to `AuditPort` bean; default is
  `InMemoryAuditLog`, durable option is `apex-audit-persistence-sqlite`).

## 15. Governance References

| Artifact | Path |
| :--- | :--- |
| Spike finding | `docs/spikes/A3-spring-boot-starter.md` |
| Governing ADR | `docs/adr/ADR-003-spring-integration-boundary.md` |
| Decision note | `docs/contracts/decisions/Q7-Q9-spring-starter.md` |
| Port contract inventory | `docs/contracts/ports.md` (all Tier 0 port rows) |
| NFR capture | `docs/contracts/nfr.md` §3 |
| Open questions | `docs/contracts/open-questions.md` (Q7, Q9 closed) |
| Spec readiness checklist | `docs/specs/README.md` |

**Decision governance:** Q7/Q9 (starter coordinates `com.apex:apex-spring-boot-starter`,
`@ConditionalOnMissingBean` on auto-config only, Tier 0 isolation,
OpenAI-compatible provider default) are closed and linked above. These
decisions constrain this spec as stated in each decision note's
"Constraint on future specs" section.
