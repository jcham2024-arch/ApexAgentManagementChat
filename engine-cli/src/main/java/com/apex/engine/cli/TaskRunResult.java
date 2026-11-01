package com.apex.engine.cli;

import java.util.Objects;

/**
 * Terminal result of a task run. Serializes to JSON via
 * {@link #toJson()} for CLI output.
 */
public record TaskRunResult(String taskId, String status, int steps,
                            long durationMs) {
    public TaskRunResult {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(status, "status");
        if (steps < 0) throw new IllegalArgumentException("steps < 0");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs < 0");
        }
    }

    public String toJson() {
        return "{\"task_id\":\"" + escape(taskId)
                + "\",\"status\":\"" + escape(status)
                + "\",\"steps\":" + steps
                + ",\"duration_ms\":" + durationMs + "}";
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.toString();
    }
}
