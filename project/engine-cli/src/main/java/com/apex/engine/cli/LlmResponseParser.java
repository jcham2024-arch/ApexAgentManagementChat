package com.apex.engine.cli;

import com.apex.engine.json.Json;

import java.util.Map;

/**
 * Parses the model's response into a {@link Decision}. The model is
 * instructed (via {@link PromptBuilder}) to reply with a JSON object
 * containing either {@code {"action": "...", "arguments": {...}}} or
 * {@code {"done": true, "summary": "..."}}. This parser tolerates
 * surrounding text and markdown code fences: it locates the first
 * balanced JSON object in the input.
 */
public final class LlmResponseParser {

    public Decision parse(String response) {
        String json = extractJson(response);
        Map<String, Object> root = Json.parseObject(json);
        if (Boolean.TRUE.equals(root.get("done"))) {
            return new Decision.Done(
                    String.valueOf(root.getOrDefault("summary", "")));
        }
        Object tool = root.get("action");
        if (!(tool instanceof String toolName) || toolName.isBlank()) {
            throw new IllegalArgumentException(
                    "response missing 'action' or 'done'");
        }
        Object args = root.getOrDefault("arguments", Map.of());
        return new Decision.Action(toolName, serializeArgs(args));
    }

    private String extractJson(String input) {
        int start = input.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("no JSON object in response");
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return input.substring(start, i + 1);
                }
            }
        }
        throw new IllegalArgumentException("unbalanced braces in response");
    }

    private String serializeArgs(Object args) {
        if (args instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(String.valueOf(e.getKey())))
                        .append("\":");
                sb.append(serializeValue(e.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        return "{}";
    }

    private String serializeValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean b) return b.toString();
        if (v instanceof Number n) return n.toString();
        if (v instanceof Map<?, ?> m) return serializeArgs(m);
        return "\"" + escape(String.valueOf(v)) + "\"";
    }

    private String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
