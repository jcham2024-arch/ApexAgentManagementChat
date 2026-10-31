package com.apex.engine.llm.http;

import java.util.Optional;

/**
 * Parses a single line from a Server-Sent Events stream. Recognizes
 * {@code data: <json>} lines and the terminal {@code data: [DONE]}
 * marker. Other lines (comments, empty lines, event type hints) are
 * ignored by returning empty.
 */
final class SseLineParser {

    private static final String PREFIX = "data: ";
    private static final String DONE_MARKER = "data: [DONE]";

    private SseLineParser() {}

    /** Returns the JSON payload if the line is a data line, else empty. */
    static Optional<String> extractData(String line) {
        if (line == null) return Optional.empty();
        if (!line.startsWith(PREFIX)) return Optional.empty();
        String payload = line.substring(PREFIX.length());
        if (payload.isBlank() || payload.equals("[DONE]")) return Optional.empty();
        return Optional.of(payload);
    }

    /** Returns true if the line is the OpenAI terminal marker. */
    static boolean isDoneMarker(String line) {
        return line != null && line.trim().equals(DONE_MARKER);
    }
}
