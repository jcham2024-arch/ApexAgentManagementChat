# Decision: Q11 — Model Provider / Spring AI Adapter Seam

## Question
Q11: MCP SDK/library choice and license compatibility (Apache/MIT/BSD/ISC).
Q11 (scope expanded): Spring AI adapter seam — adapter implements the Tier 0 model-provider port; Spring AI's `ChatClient` is an implementation detail on the Tier 1 side.

## Chosen value
**Adapter seam:** The adapter implements the Tier 0 model-provider port (`ChatModelPort`); Spring AI's `ChatClient` is an implementation detail on the Tier 1 side.
**Tool-calling integration:** Spring AI 2.0's `ToolCallingAdvisor` owns the tool loop inside the `ChatClient` advisor chain; the adapter maps the advisor's tool-call and tool-result events onto the core port's tool invocation contract.
**Determinism boundary:** The adapter is the explicit boundary where determinism may be relaxed. The core planner remains deterministic; the model call is not assumed deterministic, and the adapter must not pretend otherwise.
**Tool registration:** Tools registered through the `ChatClient` builder's `defaultTools(...)` are exposed to the adapter's tool loop; the adapter must not silently bypass the builder's tool set.
**Failure mapping:** Model-provider errors, tool-execution errors, and cancellations map onto the core's existing error-reporting and cancellation semantics without introducing new exception types in Tier 0.
**License compatibility:** Spring AI (Apache 2.0) is within the project's allowed license set (Apache 2.0 / MIT / BSD / ISC).

## Approver
AI platform engineering + core owner (30-minute decision session).

## Evidence
Spring AI 2.0 changed the shape of tool calling: the tool loop is now a first-class, composable component of the `ChatClient`'s advisor chain, owned by a `ToolCallingAdvisor` that the `DefaultChatClient` auto-registers whenever tools are present. An adapter that tries to implement its own tool loop alongside the advisor will double-execute tools or lose tool results. The correct seam is to let the advisor own the loop and map its events onto the core port. The determinism boundary is the explicit relaxation boundary — the core's determinism guarantee is about plan generation and audit ordering, not model output.

## Rationale
The port stays plain Java; the adapter absorbs the Spring AI framework. Letting `ToolCallingAdvisor` own the loop prevents double-execution of tools or lost tool results. Recording the adapter as the explicit determinism-relaxation boundary prevents a future reviewer from treating a non-deterministic model response as a determinism regression in the core. Spring AI's Apache 2.0 license is within the allowed set.

## Constraint on future specs
- The adapter must not implement a parallel tool loop inside the adapter; `ToolCallingAdvisor` owns the loop; the adapter's job is mapping, not orchestration.
- The adapter must not claim to preserve determinism; this is stated explicitly.
- The adapter must not require Spring AI types in the Tier 0 port signature; the port stays plain Java.
- Tools registered through `defaultTools(...)` must be exposed to the adapter's tool loop; the adapter must not silently bypass the builder's tool set.
- Model-provider errors, tool-execution errors, and cancellations map onto the core's existing error-reporting and cancellation semantics without introducing new exception types in Tier 0.

## Link
- Governing ADR: `docs/adr/ADR-004-model-provider-adapter-seam.md`
- Build spec: Spring AI adapter build spec (readiness gate blocked on Q11; now cleared)
