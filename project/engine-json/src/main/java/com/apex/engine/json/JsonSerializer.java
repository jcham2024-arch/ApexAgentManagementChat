package com.apex.engine.json;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal JSON serializer. Converts {@code Map}, {@code List}, {@code String},
 * {@code Number}, {@code Boolean}, and {@code null} values to JSON text.
 */
public final class JsonSerializer {

    private JsonSerializer() {}

    /**
     * Serializes a {@code Map} to a JSON string. Supports {@code String},
     * {@code Number}, {@code Boolean}, {@code null}, {@code List}, and
     * nested {@code Map} values.
     */
    public static String stringify(Map<String, Object> map) {
        Objects.requireNonNull(map, "map");
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escapeString(entry.getKey())).append('"');
            sb.append(':');
            sb.append(valueToJson(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return '"' + escapeString((String) value) + '"';
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map) {
            return stringify((Map<String, Object>) value);
        }
        if (value instanceof List) {
            return valueToJsonArray((List<?>) value);
        }
        throw new JsonException("unsupported value type: " + value.getClass().getName());
    }

    private static String valueToJsonArray(List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(valueToJson(item));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escapeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
