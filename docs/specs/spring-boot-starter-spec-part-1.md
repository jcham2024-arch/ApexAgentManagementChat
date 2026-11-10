# BUILD-SPEC: Spring Boot Starter (`apex-spring-boot-starter`) — Part 1 of 2

**Source:** docs/specs/spring-boot-starter-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 1 of 2
**Cross-reference:** See `spring-boot-starter-spec-part-2.md` for sections 7-15.

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
| **Spec readiness** | **READY** — Q7/Q9 closed, ADR-003 accepted, port classification |
| | unchanged |

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
| `planLoop` | `PlanLoop` | Yes | Yes |
| `auditPort` | `AuditPort` | Yes | Yes |
| `chatModelPort` | `ChatModelPort` | Yes | Yes |
| `toolExecutorPort` | `ToolExecutorPort` | Yes | Yes |
| `storagePort` | `StoragePort` | Yes | Yes |
| `checkpointPort` | `CheckpointPort` | Yes | Yes |
| `runtimePolicy` | `RuntimePolicy` | Yes | Yes |
| `gatePlacementPolicy` | `GatePlacementPolicy` | Yes | Yes |
| `secretsPort` | `SecretsPort` | Yes | Yes |
| `metricsPort` | `MetricsPort` | Yes | Yes |
| `telemetryPort` | `TelemetryPort` | Yes | Yes |

### Port classification

- **All Tier 0 ports**: **unchanged** — no method signatures or signatures |
| altered. The starter only *consumes* ports as beans; it does not modify |
| any port interface.
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
| `org.springframework.boot:spring-boot-starter` | Compile | Spring Boot |
| | | auto-configuration foundation (Tier 2 allowed). |
| `org.springframework.boot:spring-boot` | Compile | `@Configuration`, |
| | | `@ConditionalOnMissingBean`, `@ConfigurationProperties` (Tier 2 |
| | | allowed). |
| `com.apex:engine-core` | Compile | Tier 0 ports and kernel (project dependency). |
| `com.apex:engine-planner` | Compile | `AStarPlanner`, `Blackboard`, `Goal` |
| | | (project dependency). |
| `com.apex:engine-tools` | Compile | Tool metadata and policy records |
| | | (project dependency). |
| `com.apex:engine-store` | Compile | `StoragePort`, `CheckpointPort` |
| | | interfaces (project dependency). |
| `com.apex:engine-json` | Compile | Minimal JSON parser |
| | | (project dependency). |
| `com.apex:engine-memory` | Compile | Memory, compactor, router |
| | | (project dependency). |
| `org.jspecify` | Compile | Null-safety annotations per project |
| | | standards. |
| `org.junit.jupiter:junit-jupiter` | Test | Test framework per project |
| | | standard. |
| `org.assertj:assertj-core` | Test | Assertions per project |
| | | standard. |
| `com.tngtech.archunit:archunit-junit5` | Test | Architectural |
| | | enforcement per project standard. |

**Tier 0 purity:** Zero Spring types in `engine-core`, `engine-planner`,
`engine-tools`, `engine-store`, `engine-json`, or `engine-memory`. The
starter depends on Tier 0; Tier 0 never depends on the starter.
`CorePurityTest` must pass for all Tier 0 modules.

## 6. Implementation Design

### 6.1 Auto-configuration class

A single `@Configuration` class (`ApexAutoConfiguration`) provides one
`@Bean` per port, each annotated `@ConditionalOnMissingBean`. When a
caller supplies their own bean for a port, Spring skips the
auto-configured default. `@ConditionalOnClass` and `@ConditionalOnProperty`
gate optional integrations (e.g., Spring AI adapter only when
`spring-ai` is on the classpath).

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

Cancellation is exposed as a bean method or application event — **not**
as a Spring-specific type leaking into the core.

### 6.4 Auth integration

Auth integrates as a Spring `OncePerRequestFilter` (or
`HandlerInterceptor`): extracts the JWT from the `Authorization` header,
verifies via the `TokenVerifier`/`AuthFilter` bean, and places the
`Principal`/`AgentIdentity` in a request-scoped `ExecutionContext`
copied at the HTTP filter boundary (one ThreadLocal read allowed, per
AGENT_RULES §2.5).

### 6.5 Configuration surface

Grouped `@ConfigurationProperties` classes (`apex.audit`,
`apex.plan-loop`, etc.) rather than dozens of scattered `@Value`
injections.
