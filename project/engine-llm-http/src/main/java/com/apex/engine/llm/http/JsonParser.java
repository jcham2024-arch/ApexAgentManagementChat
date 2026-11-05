package com.apex.engine.llm.http;

import com.apex.engine.json.Json;
import com.apex.engine.json.JsonException;

import java.util.Map;

/**
 * Minimal JSON parser for provider payloads. Delegates to
 * {@link com.apex.engine.json.Json} and wraps {@link JsonException} as
 * {@link LlmException}. Delegation avoids duplication with
 * {@code engine-tools-impl.Json}.
 */
public final class JsonParser {

    private JsonParser() {}

    /**
     * Parses a JSON object string into a {@code Map}. Throws
     * {@link LlmException} on malformed input.
     */
    public static Map<String, Object> parseObject(String input) {
        try {
            return com.apex.engine.json.Json.parseObject(input);
        } catch (JsonException e) {
            throw new LlmException("json", e.getMessage(), e);
        }
    }
}
