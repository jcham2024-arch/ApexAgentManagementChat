# PHASE-S2.4-MODEL-ADAPTER-METADATA-TESTS.md — Session Package, Part 7 of 8

**Version:** 1.0 · **Sub-phase of S2** · **Part 7 of 8:** §5.8 and §6, metadata and the test matrix.
Parts: SESSION · CORE-PORT · LLM-BASICS · CONFIG · PROTOCOL · PROVIDER ·
METADATA-TESTS · CRITERIA, each `PHASE-S2.4-MODEL-ADAPTER-<part>.md`
(`AGENT_RULES.md` §2.13).

---
### 5.8 Update `engine-llm-http` metadata

**`engine-llm-http/src/main/java/com/apex/engine/llm/http/package-info.java`** — replace content:

```java
/**
 * Default ChatModelPort implementation for OpenAI-compatible providers.
 * Tier 1. Implements: ChatModelPort. Consumes: java.base,
 * java.net.http. Handles streaming, error classification, and
 * cancellation between chunks.
 */
@NullMarked
package com.apex.engine.llm.http;

import org.jspecify.annotations.NullMarked;
```

**`engine-llm-http/README.md`** — replace content:

```markdown
# engine-llm-http

**Tier:** 1 · **Purpose:** OpenAI-compatible ChatModelPort over java.net.http.

## Allowed dependencies
java.base, java.net.http, jspecify

## Ports consumed
none

## Ports implemented
ChatModelPort (via OpenAiCompatibleProvider)

## Configuration
ProviderConfigLoader.fromEnv() reads (in priority order):
- APEX_LLM_BASE_URL + APEX_LLM_API_KEY (+ optional APEX_LLM_DEFAULT_MODEL, APEX_LLM_TIMEOUT_SEC)
- OPENROUTER_API_KEY (base URL defaults to openrouter.ai/api/v1)
- NVIDIA_API_KEY (integrate.api.nvidia.com/v1)
- DEEPSEEK_API_KEY (api.deepseek.com/v1)
- GROQ_API_KEY (api.groq.com/openai/v1)

## Tests
ChatModelPortDefaultStreamTest · ChatChunkTest · JsonParserTest ·
ProviderConfigLoaderTest · SseLineParserTest · OpenAiProtocolTest ·
OpenAiCompatibleProviderChatTest · OpenAiCompatibleProviderStreamTest
```

## 6. Tests

### 6.1 `engine-core` tests

**`ChatChunkTest.java`** — accepts `("x", false, null)`, accepts
`("", true, "stop")`, rejects null delta, rejects `done=true` with null
`finishReason`, equality is value-based.

**`ChatModelPortDefaultStreamTest.java`** — the default method emits
one terminal chunk with `chat()`'s content; the default method respects
cancellation (cancelled token → zero chunks).

### 6.2 `engine-llm-http` tests

**`JsonParserTest.java`** — the same coverage as S2.3's `JsonTest`,
plus one test proving `LlmException` is thrown (not
`ToolException`).

**`ProviderConfigLoaderTest.java`** — six cases:
- explicit `APEX_LLM_BASE_URL` set → uses it
- `OPENROUTER_API_KEY` set, no explicit → uses openrouter.ai URL
- `NVIDIA_API_KEY` set → uses NIM URL
- `DEEPSEEK_API_KEY` set → uses deepseek URL
- `GROQ_API_KEY` set → uses groq URL
- nothing set → throws `LlmException("config", ...)`

**`SseLineParserTest.java`** — `data: {...}` returns the JSON,
`data: [DONE]` is detected, empty line returns empty, comment line
returns empty, null returns empty.

**`OpenAiProtocolTest.java`** — pure unit tests:
- `buildBody("m", ["a","b"], false)` produces valid JSON with
  `"stream":false`
- `buildBody` escapes quotes and newlines in prompt parts
- `parseChatResponse` extracts content from a fixture body
- `parseChatResponse` throws on missing choices
- `parseChunk` returns the delta and finish_reason
- `parseChunk` returns `Optional.empty()` for chunks without content
- `mapHttpError(401)` → category "auth"
- `mapHttpError(429)` → category "rate_limit"
- `mapHttpError(500)` → category "server"
- `mapHttpError(404)` → category "client"

**`OpenAiCompatibleProviderChatTest.java`** — mock HTTP server via
`com.sun.net.httpserver.HttpServer` on ephemeral port. Five tests:
- happy path returns `ChatResponse` with content
- empty `modelId` uses default model (verify request body contains
  `"model":"<default>"`)
- 401 → `LlmException` with category "auth"
- 429 → `LlmException` with category "rate_limit"
- 500 → `LlmException` with category "server"

**`OpenAiCompatibleProviderStreamTest.java`** — mock server, four
tests:
- happy path: server sends two delta chunks then `[DONE]`; consumer
  receives 3 chunks (2 content + 1 terminal)
- content-only termination: server sends deltas with `finish_reason`
  in last chunk, no `[DONE]`; consumer receives terminal chunk from
  `finish_reason`
- cancellation: server streams chunks every 20ms; cancel at 100ms;
  assert fewer than total expected chunks received and stream returned
- error status: server responds 500 to streaming request; assert
  `LlmException` category "server"

Each test class ≤ 200 lines. Split if exceeded.
