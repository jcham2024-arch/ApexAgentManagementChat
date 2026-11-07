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
