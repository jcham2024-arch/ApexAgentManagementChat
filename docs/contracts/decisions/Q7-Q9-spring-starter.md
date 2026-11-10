# Decision: Q7/Q9 — Spring Boot Starter and Integration Boundary

## Question
Q7: Spring Boot starter module name/coordinates (groupId/artifactId).
Q9: Spring AI version and provider (OpenAI-compatible vs Azure vs Ollama).

## Chosen value
**Starter shape:** A dedicated `apex-spring-boot-starter` module that auto-configures the kernel's ports and `PlanLoop` as Spring beans.
**Conditional beans:** Every auto-configured bean guarded by `@ConditionalOnMissingBean` so a caller's own adapter takes precedence. `@ConditionalOnClass` and `@ConditionalOnProperty` used to gate optional integrations.
**Conditional scope:** `@ConditionalOnMissingBean` used only on auto-configuration classes, per Spring's own documentation; not on regular `@Configuration` classes.
**Tier 0 isolation:** No Spring type may appear in `engine-core`, `engine-planner`, or `engine-tools`. The starter is a Tier 1 module that depends on Tier 0; Tier 0 never depends on it.
**PlanLoop lifecycle:** Managed as a Spring bean with explicit start/stop hooks; cancellation exposed as a bean method or application event, not as a Spring-specific type leaking into the core.
**Auth integration:** `TokenVerifier` wired as a filter or interceptor at the platform boundary; the port remains a plain Java interface in Tier 0.
**Configuration surface:** Grouped `@ConfigurationProperties` classes rather than dozens of `@Value` injections.
**Starter coordinates:** groupId `com.apex`, artifactId `apex-spring-boot-starter` (Q7 resolved).
**Spring AI provider:** OpenAI-compatible provider as the default (Q9 resolved); provider is starter configuration, not a Tier 0 concern.

## Approver
Platform engineering + Spring integration owner (30-minute decision session).

## Evidence
Spring Boot auto-configuration is a well-trodden path with known failure modes:
- `@ConditionalOnMissingBean` is the mechanism that makes a starter adoptable; it lets an existing application override any default adapter without forking the starter, and it is the annotation every mature Spring Boot starter uses for its auto-configured beans.
- Spring's own documentation is explicit that using `@ConditionalOnMissingBean` on regular `@Configuration` classes is unreliable because evaluation timing depends on bean registration order.
- The Tier 0 isolation rule is the project's own architectural invariant and keeps `CorePurityTest` meaningful: a Spring application can depend on the starter without the core gaining a Spring dependency.

## Rationale
The starter shape gives Java enterprises an adoptable on-ramp — they arrive through Spring Boot. `@ConditionalOnMissingBean` on auto-configuration classes ensures caller-supplied adapters take precedence without forking. Tier 0 isolation preserves the `CorePurityTest` invariant. `PlanLoop` lifecycle as a Spring bean with explicit start/stop hooks prevents shutdown bugs. `@ConfigurationProperties` groups configuration surface rather than dozens of `@Value` injections.

## Constraint on future specs
- `@ConditionalOnMissingBean` must be used only on auto-configuration classes, never on regular `@Configuration` classes.
- No Spring type may appear in `engine-core`, `engine-planner`, or `engine-tools`; Spring types are confined to the starter module.
- `PlanLoop` must be manageable as a Spring bean with explicit start/stop hooks; cancellation exposed without Spring-specific types leaking into the core.
- The starter must not auto-configure `PlanLoop` without an explicit stop hook.
- Spring AI provider (OpenAI-compatible default) is starter configuration; Tier 0 port signatures remain plain Java.

## Link
- Governing ADR: `docs/adr/ADR-003-spring-integration-boundary.md`
- Build spec: Spring Boot starter build spec (readiness gate blocked on Q7/Q9; now cleared)
