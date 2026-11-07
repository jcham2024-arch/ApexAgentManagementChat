package com.apex.engine.llm.http;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderConfigLoaderTest {

    @Test
    void explicitBaseUrlTakesPriority() {
        ProviderConfig config = ProviderConfigLoader.fromMap(Map.of(
                "APEX_LLM_BASE_URL", "https://example.test/v1",
                "APEX_LLM_API_KEY", "secret",
                "APEX_LLM_DEFAULT_MODEL", "my-model",
                "OPENROUTER_API_KEY", "ignored"));

        assertThat(config.baseUrl()).isEqualTo("https://example.test/v1");
        assertThat(config.apiKey()).isEqualTo("secret");
        assertThat(config.defaultModel()).isEqualTo("my-model");
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void openRouterKeySelectsOpenRouter() {
        ProviderConfig config = ProviderConfigLoader.fromMap(
                Map.of("OPENROUTER_API_KEY", "k"));

        assertThat(config.baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(config.apiKey()).isEqualTo("k");
    }

    @Test
    void nvidiaKeySelectsNim() {
        ProviderConfig config = ProviderConfigLoader.fromMap(
                Map.of("NVIDIA_API_KEY", "k"));

        assertThat(config.baseUrl())
                .isEqualTo("https://integrate.api.nvidia.com/v1");
    }

    @Test
    void deepSeekKeySelectsDeepSeek() {
        ProviderConfig config = ProviderConfigLoader.fromMap(
                Map.of("DEEPSEEK_API_KEY", "k"));

        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com/v1");
    }

    @Test
    void groqKeySelectsGroq() {
        ProviderConfig config = ProviderConfigLoader.fromMap(
                Map.of("GROQ_API_KEY", "k"));

        assertThat(config.baseUrl()).isEqualTo("https://api.groq.com/openai/v1");
    }

    @Test
    void noProviderConfiguredThrows() {
        assertThatThrownBy(() -> ProviderConfigLoader.fromMap(Map.of()))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("no LLM provider configured");
    }

    @Test
    void trailingSlashIsNormalized() {
        ProviderConfig config = ProviderConfigLoader.fromMap(Map.of(
                "APEX_LLM_BASE_URL", "https://example.test/v1/",
                "APEX_LLM_API_KEY", "secret"));

        assertThat(config.baseUrl()).isEqualTo("https://example.test/v1");
    }

    @Test
    void timeoutIsReadAndValidated() {
        ProviderConfig config = ProviderConfigLoader.fromMap(Map.of(
                "APEX_LLM_BASE_URL", "https://example.test/v1",
                "APEX_LLM_API_KEY", "secret",
                "APEX_LLM_TIMEOUT_SEC", "5"));

        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThatThrownBy(() -> ProviderConfigLoader.fromMap(Map.of(
                "APEX_LLM_BASE_URL", "https://example.test/v1",
                "APEX_LLM_API_KEY", "secret",
                "APEX_LLM_TIMEOUT_SEC", "soon")))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("invalid APEX_LLM_TIMEOUT_SEC");
    }

    @Test
    void missingApiKeyForExplicitBaseUrlThrows() {
        assertThatThrownBy(() -> ProviderConfigLoader.fromMap(
                Map.of("APEX_LLM_BASE_URL", "https://example.test/v1")))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("missing env: APEX_LLM_API_KEY");
    }
}
