---
bundle: f2-model-minimum
profile: 13-layer1-build
target_branch: main
file_count: 3
file_index:
  - .agents/tasks/F2-model-minimum.md
  - apex-chat/apex-chat-common/pom.xml
  - apex-chat/apex-chat-core/pom.xml
---

## Agent Instructions

This bundle creates the task specification and the two module POMs.
The Java source is generated in the same session by you, from the task
spec, not by this bundle.

Sequence:

1. Write the 3 files shown below.
2. Commit the POMs and the task file as
   `build(apex): add core module and F2 task specification`.
3. Read `.agents/tasks/F2-model-minimum.md` in full.
4. Execute it. That task tells you exactly which types to produce,
   in which packages, with which tests. Follow it, do not improvise.
5. When the task says "run CI," push and wait for green. If CI fails,
   diagnose, fix, push again — up to 3 attempts. If still red after 3,
   stop and report the failing log.
6. Report: files created, test count, CI link, any judgment calls.
   Do NOT claim "done" until CI is green.

You may produce as many files as the task requires. Do not produce files
the task does not name.

## Files

**.agents/tasks/F2-model-minimum.md**
```markdown
# Task F2 — Layer 1 Minimum Viable Model

**Input:** `docs/apex-chat-spec/` (Parts 1–10 + File 11 amendments)
**Output:** A compilable, tested subset of Layer 1 that unblocks
`engine-llm` and `engine-core`.
**Verification:** `mvn -B test` green in CI; ArchUnit passes.
**Do not produce:** any type from `apex-chat-tools` or `apex-chat-agent`
beyond what this task names. Do not produce `engine-*`.

---

## Scope

Produce Layer 1 types from these package groups, in this order:

### Group A — `apex-chat-common` (identity, meta, schema, validation)

Package `com.apex.chat.common.identity`:

- `TenantId` (already exists — leave as-is)
- `TypedId<T>` — `record TypedId<T>(String value)`
- `Reference<T>` — `record Reference<T>(RefKind kind, String id)`
- `OpaqueRef` — `record OpaqueRef(String id)`
- `RefKind` — sealed: `Chat, Message, Participant, File, Memory, Artifact, Tool, Skill, Plugin, Agent, Model, Prompt, Content, Thread, Turn, Run, Step, Custom(name)`
- `ResourceRef` — `record ResourceRef(String uri, ResourceKind kind, @Nullable String mimeHint)`
- `WorkspacePath` — `record WorkspacePath(String value)` with non-blank + no `..` validation

Package `com.apex.chat.common.meta`:

- `MetadataCarrier` interface
- `MetadataKey` — `record MetadataKey(String namespace, String key)`
- `MetadataValue` — sealed: `StringValue, NumberValue, BooleanValue, InstantValue, JsonValue`
- `DisplayName`, `Description`, `Tag` — simple value records
- `SemanticVersion` — `record SemanticVersion(int major, int minor, int patch, @Nullable String preRelease)`

Package `com.apex.chat.common.schema`:

- `JsonSchema` — `record JsonSchema(String document, String dialect, @Nullable String ref)`
- `SchemaRef` — `record SchemaRef(String id, @Nullable String uri)`

Package `com.apex.chat.common.validation`:

- `Validatable` interface
- `ValidationResult` — `record ValidationResult(boolean valid, List<Violation> violations)`
- `Violation` — `record Violation(String path, String message, Severity severity)`
- `Severity` — sealed: `Info, Warning, Error`

Package `com.apex.chat.common.traversal`:

- `Visitor<R>` marker interface
- `BlockVisitor<R>` — one visit per ContentBlock variant (declared in Group B; this file may reference types not yet produced, so **produce BlockVisitor last, after Group B**)

**Group A test count target:** ~20 unit tests covering null rejection, blank rejection, defensive copy, and equality for every record. One `ArchUnit` test module setup (see §Verification).

### Group B — `apex-chat-core` minimum

Package `com.apex.chat.core.content`:

- `ContentBlock` sealed interface with methods `blockId()`, `kind()`
- `TextBlock` — record
- `ImageBlock` — record
- `ToolCallBlock` — record (references `ToolCall` from Group B tools-lite below)
- `ToolResultBlock` — record
- `ContentKind` — sealed matching block variants (minimum: `Text, Image, ToolCall, ToolResult`)
- `BlockMetadata` — record
- `MediaSource` — sealed: `Inline, Url, FileRefRef` (rename `FileRef` → `FileRefRef` to avoid name collision with the `FileRef` record in Group B storage)

Package `com.apex.chat.core.model`:

- `TokenUsage` — `record TokenUsage(int promptTokens, int completionTokens, int cachedTokens, int reasoningTokens, int totalTokens)` with compact-ctor validation: `totalTokens == promptTokens + completionTokens + reasoningTokens`; `cachedTokens <= promptTokens`
- `GenerationTelemetry` — record per Part 3
- `CostEstimation` — record per amendment §4.1, with `total()` method and `BigDecimal`
- `ModelDescriptor` — record per Part 3
- `ModelPricing` — record per Part 3
- `ModelCapabilities` — record per Part 3
- `ContextWindowSpecification` — record per Part 3
- `ModelSpecification` — record per Part 3
- `FinishReason` — sealed per Part 3
- `CacheStatus` — sealed per Part 3

Package `com.apex.chat.core.message`:

- `Message` sealed interface with methods `messageId()`, `role()`, `content()`, `authorRef()`, `timestamp()`
- `SystemMessage`, `UserMessage`, `AssistantMessage` — records (three minimum variants; `DeveloperMessage`, `ToolMessage`, `FunctionMessage` deferred to F3)
- `MessageRole` — sealed: `System, User, Assistant` (three for now)

Package `com.apex.chat.core.participant`:

- `ChatParticipant` interface with `participantId()`, `participantKind()`, `displayName()`
- `ChatParticipantRef` — `record ChatParticipantRef(TypedId<ChatParticipant> participantId, ParticipantKind participantKind)`
- `ParticipantKind` — sealed: `User, Llm, System` (three for now; `Agent`, `External` deferred to F4)
- `ChatUser` — record implementing `ChatParticipant`
- `LlmModelParticipant` — record implementing `ChatParticipant` with `modelDescriptor: ModelDescriptor`

Package `com.apex.chat.core.thread`:

- `ConversationThread` — record with `threadId`, `turns`
- `ConversationTurn` — record with `turnId`, `index`, `speaker`, `messages`
- `TurnKind` — sealed: `SingleSpeaker, SystemEvent` (two for now)

Package `com.apex.chat.core.chat.identity` (per amendment §3.1):

- `ChatIdentity` — `record ChatIdentity(TenantId tenantId, TypedId<Chat> chatId, Instant createdAt, PlatformProfileRef platformProfileRef)`
- `PlatformProfileRef` — record

Package `com.apex.chat.core.chat.lifecycle` (per amendment §3.1):

- `ChatLifecycle` — `record ChatLifecycle(SessionStatus status, ChatInterface iface, PromptContext promptContext)`
- `SessionStatus` — sealed: `Active, Paused, Closed, Errored`
- `ChatInterface` — record
- `PromptContext` — record (minimal shape: `systemPromptRef: OpaqueRef`)

Package `com.apex.chat.core.chat`:

- `Chat` — record with the 8 fields per amendment §3.2. Sub-aggregates not needed for F2 (`ChatConfiguration`, `ChatMembership`, `ChatStorage`, `ChatExecution`, `ChatJournal`, `ConversationThread`); produce them as minimal records with `Map<String, String> metadata` placeholder fields where the full shape would require types from F3+.

Package `com.apex.chat.core.tools.lite` (a temporary bridge until `apex-chat-tools` exists in F4):

- `ToolCall` — record with `callId: String`, `toolName: String`, `arguments: String` (JSON)
- `ToolResult` — record with `callId: String`, `output: String`, `isError: boolean`, `durationMs: long`

These bridge types are **deprecated on production** and will be replaced by the full `apex-chat-tools` types in F4. Mark them `@Deprecated(forRemoval = true)` in Javadoc with a note pointing to F4.

**Group B test count target:** ~30 unit tests. `TokenUsage` and `CostEstimation` need the arithmetic invariants from amendment §7. `ContentBlock`, `Message`, `ParticipantKind`, `SessionStatus`, `TurnKind` need exhaustiveness tests (a switch with no default must compile).

### Group C — ArchUnit

Add `archunit-junit5` to `apex-chat-common/pom.xml` and
`apex-chat-core/pom.xml`. Create two test classes:

- `apex-chat/apex-chat-common/src/test/java/com/apex/chat/common/CommonArchitectureTest.java` — asserts:
  - No `java.io`, `java.nio.file`, `java.net`, `java.lang.reflect`, `java.util.concurrent` imports in any class under `common/`
  - Every package has `package-info.java` with `@NullMarked`
  - No `Optional` in record components or method parameters
- `apex-chat/apex-chat-core/src/test/java/com/apex/chat/core/CoreArchitectureTest.java` — asserts:
  - `core` imports only `common` (and JDK)
  - Same forbidden-imports rule as `common`
  - Same `@NullMarked` rule

The ArchUnit classes are the primary guarantee the model stays pure. They are not optional.

---

## Verification gates

The task is complete when **all** of the following are true in CI:

1. `mvn -B test` passes.
2. `apex-chat-common` and `apex-chat-core` both build.
3. `CommonArchitectureTest` and `CoreArchitectureTest` pass.
4. Total test count is ≥ 50 across both modules.
5. Every record with a collection component uses `List.copyOf` /
   `Map.copyOf` in its compact constructor (verified by at least one
   test per record — a mutation attempt throws).

If CI is green, the task is done. Report file count, test count, CI
link, and any judgment calls (e.g., a name collision that required a
rename, or a type left as `Object` because a downstream type is not
yet produced).

If CI is red after 3 attempts, stop and report the failing log. Do not
attempt a 4th fix without human input.

---

## Explicit non-goals

Do not produce in this task:

- Any type from `apex-chat-capability`
- Any type from `apex-chat-tools` beyond the `tools.lite` bridge types
- Any type from `apex-chat-agent`
- Any type from `engine-*`
- Any streaming event type (`StreamingEvent`, `WireEvent`)
- Any `CostEstimation` variant beyond the base record
- Any provider adapter (Anthropic, OpenAI) — those are F3
- Any HTTP controller — that is F5
- Any client code — that is F6

If a type is not named in Group A or Group B, do not produce it. If
something seems needed, leave a `// TODO(F<n>):` comment and move on.
```

**apex-chat/apex-chat-common/pom.xml** (update the existing one)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.apex</groupId>
        <artifactId>apex-chat</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>apex-chat-common</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.jspecify</groupId>
            <artifactId>jspecify</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>net.jqwik</groupId>
            <artifactId>jqwik</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>1.3.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**apex-chat/apex-chat-core/pom.xml** (new)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.apex</groupId>
        <artifactId>apex-chat</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>apex-chat-core</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.apex</groupId>
            <artifactId>apex-chat-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jspecify</groupId>
            <artifactId>jspecify</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>net.jqwik</groupId>
            <artifactId>jqwik</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>1.3.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Also update `apex-chat/pom.xml` to include the new module:
```xml
<modules>
    <module>apex-chat-common</module>
    <module>apex-chat-core</module>
</modules>
```

## Commit Plan

1. `build(apex): add apex-chat-core module and archunit to common`
2. `docs(tasks): add F2-model-minimum specification`
3. (agent produces ~55 java files in ~15 logical commits per the task)
4. `test(apex): mark F2 complete — mvn test green in CI`