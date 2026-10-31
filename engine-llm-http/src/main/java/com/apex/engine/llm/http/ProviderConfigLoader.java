package com.apex.engine.llm.http;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Reads provider configuration from environment variables. Explicit
 * `APEX_LLM_*` variables take priority; otherwise known providers are
 * detected by their API key env var and get a default base URL and
 * model.
 *
 * <p>{@link #fromEnv()} delegates to {@link #fromMap(Map)} with
 * {@code System.getenv()}. Tests call {@code fromMap} to avoid live
 * environment dependence.
 */
public final class ProviderConfigLoader {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final String DEFAULT_MODEL =
            "meta-llama/llama-3.3-70b-instruct:free";

    private ProviderConfigLoader() {}

    public static ProviderConfig fromEnv() {
        return fromMap(System.getenv());
    }

    public static ProviderConfig fromMap(Map<String, String> env) {
        Objects.requireNonNull(env, "env");
        if (env.containsKey("APEX_LLM_BASE_URL")) {
            return explicit(env);
        }
        if (env.containsKey("OPENROUTER_API_KEY")) {
            return known(env, "OPENROUTER",
                    "https://openrouter.ai/api/v1", DEFAULT_MODEL);
        }
        if (env.containsKey("NVIDIA_API_KEY")) {
            return known(env, "NVIDIA",
                    "https://integrate.api.nvidia.com/v1",
                    "openai/gpt-oss-120b");
        }
        if (env.containsKey("DEEPSEEK_API_KEY")) {
            return known(env, "DEEPSEEK",
                    "https://api.deepseek.com/v1",
                    "deepseek-v4-flash");
        }
        if (env.containsKey("GROQ_API_KEY")) {
            return known(env, "GROQ",
                    "https://api.groq.com/openai/v1",
                    "openai/gpt-oss-120b");
        }
        throw new LlmException("config",
                "no LLM provider configured; set APEX_LLM_BASE_URL + "
                + "APEX_LLM_API_KEY, or one of OPENROUTER_API_KEY, "
                + "NVIDIA_API_KEY, DEEPSEEK_API_KEY, GROQ_API_KEY");
    }

    private static ProviderConfig explicit(Map<String, String> env) {
        return new ProviderConfig(
                require(env, "APEX_LLM_BASE_URL"),
                require(env, "APEX_LLM_API_KEY"),
                env.getOrDefault("APEX_LLM_DEFAULT_MODEL", DEFAULT_MODEL),
                parseTimeout(env.get("APEX_LLM_TIMEOUT_SEC")));
    }

    private static ProviderConfig known(Map<String, String> env,
                                        String prefix, String baseUrl,
                                        String defaultModel) {
        return new ProviderConfig(
                baseUrl,
                require(env, prefix + "_API_KEY"),
                env.getOrDefault(prefix + "_DEFAULT_MODEL", defaultModel),
                parseTimeout(env.get("APEX_LLM_TIMEOUT_SEC")));
    }

    private static String require(Map<String, String> env, String key) {
        String v = env.get(key);
        if (v == null || v.isBlank()) {
            throw new LlmException("config", "missing env: " + key);
        }
        return v;
    }

    private static Duration parseTimeout(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_TIMEOUT;
        try {
            long sec = Long.parseLong(raw.trim());
            if (sec <= 0) throw new NumberFormatException();
            return Duration.ofSeconds(sec);
        } catch (NumberFormatException e) {
            throw new LlmException("config",
                    "invalid APEX_LLM_TIMEOUT_SEC: " + raw);
        }
    }
}
