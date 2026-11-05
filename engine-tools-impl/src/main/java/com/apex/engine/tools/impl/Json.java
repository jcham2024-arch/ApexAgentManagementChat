package com.apex.engine.tools.impl;

import com.apex.engine.json.JsonException;

import java.util.Map;

/**
 * Minimal JSON parser for tool arguments. Delegates to
 * {@link com.apex.engine.json.Json} and wraps {@link JsonException} as
 * {@link ToolException}. Delegation avoids duplication with
 * {@code engine-llm-http.JsonParser}.
 */
public final class Json {

    private Json() {}

    /**
     * Parses a JSON object string into a {@code Map}. Throws
     * {@link ToolException} on malformed input.
     */
    public static Map<String, Object> parseObject(String input) {
        try {
            return com.apex.engine.json.Json.parseObject(input);
        } catch (JsonException e) {
            throw new ToolException("json", e.getMessage(), e);
        }
    }
}
