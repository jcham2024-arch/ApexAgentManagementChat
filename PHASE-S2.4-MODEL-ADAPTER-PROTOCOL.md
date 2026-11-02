# PHASE-S2.4-MODEL-ADAPTER-PROTOCOL.md — Session Package, Part 5 of 8

**Version:** 1.0 · **Sub-phase of S2** · **Part 5 of 8:** §5.5–§5.6, SseLineParser and OpenAiProtocol.
Parts: SESSION · CORE-PORT · LLM-BASICS · CONFIG · PROTOCOL · PROVIDER ·
METADATA-TESTS · CRITERIA, each `PHASE-S2.4-MODEL-ADAPTER-<part>.md`
(`AGENT_RULES.md` §2.13).

---
### 5.5 `SseLineParser.java`

```java
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
```

### 5.6 `OpenAiProtocol.java`

```java
package com.apex.engine.llm.http;

import com.apex.engine.core.port.model.ChatChunk;
import com.apex.engine.core.port.model.ChatResponse;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

/**
 * Building blocks for the OpenAI chat-completions HTTP contract. Not a
 * port implementation. Keeps {@link OpenAiCompatibleProvider} under the
 * 200-line limit.
 */
final class OpenAiProtocol {

    private OpenAiProtocol() {}

    static String buildBody(String model, List<String> promptParts,
                            boolean stream) {
        String content = String.join("\n\n", promptParts);
        StringBuilder sb = new StringBuilder(64 + content.length() * 2);
        sb.append("{\"model\":").append(quote(model));
        sb.append(",\"messages\":[{\"role\":\"user\",\"content\":")
          .append(quote(content)).append("}]");
        sb.append(",\"stream\":").append(stream);
        sb.append('}');
        return sb.toString();
    }

    static HttpRequest buildRequest(ProviderConfig cfg, String body,
                                    boolean stream) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(cfg.baseUrl() + "/chat/completions"))
                .timeout(cfg.timeout())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (stream) b.header("Accept", "text/event-stream");
        return b.build();
    }

    static ChatResponse parseChatResponse(String body) {
        Map<String, Object> root = JsonParser.parseObject(body);
        Map<String, Object> choice = firstChoice(root);
        @SuppressWarnings("unchecked")
        Map<String, Object> message =
                (Map<String, Object>) choice.get("message");
        if (message == null) {
            throw new LlmException("parse", "response missing message");
        }
        Object content = message.get("content");
        return new ChatResponse(content == null ? "" : content.toString());
    }

    static ChatChunk parseChunk(String dataJson) {
        Map<String, Object> root = JsonParser.parseObject(dataJson);
        if (root.containsKey("choices")) {
            @SuppressWarnings("unchecked")
            List<Object> choices = (List<Object>) root.get("choices");
            if (!choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> choice = (Map<String, Object>) choices.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> delta =
                        (Map<String, Object>) choice.get("delta");
                String content = "";
                if (delta != null && delta.get("content") != null) {
                    content = delta.get("content").toString();
                }
                Object finish = choice.get("finish_reason");
                boolean done = finish != null;
                String reason = done ? finish.toString() : null;
                // Empty done chunks must have a reason; others may not.
                if (content.isEmpty() && !done) {
                    return new ChatChunk("", false, null) {
                        // placeholder avoided below
                    };
                }
                return new ChatChunk(content, done, reason);
            }
        }
        return emptyChunk();
    }

    static LlmException mapHttpError(int status, String body) {
        String category;
        if (status == 401 || status == 403) category = "auth";
        else if (status == 429) category = "rate_limit";
        else if (status >= 500) category = "server";
        else category = "client";
        String snippet = body.length() > 500 ? body.substring(0, 500) : body;
        return new LlmException(category,
                "HTTP " + status + ": " + snippet);
    }

    private static Map<String, Object> firstChoice(Map<String, Object> root) {
        @SuppressWarnings("unchecked")
        List<Object> choices = (List<Object>) root.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmException("parse", "response missing choices");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> choice = (Map<String, Object>) choices.get(0);
        return choice;
    }

    private static ChatChunk emptyChunk() {
        // An empty non-terminal chunk: not valid per ChatChunk's contract
        // (needs finishReason only when done). Construct via terminal
        // marker only — callers should ignore this case. Emit a benign
        // no-op by returning a done=true chunk with an empty delta.
        return new ChatChunk("", true, "stop");
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
```

**Note for the agent:** the `emptyChunk` helper above is awkward. Cleaner approach: change `ChatChunk.parseChunk` to return `Optional<ChatChunk>`, so a chunk that has no content and no finish is simply not emitted. Adjust `OpenAiCompatibleProvider` accordingly. The 200-line limit is not worth a fragile contract. **Preferred implementation:** return `Optional<ChatChunk>` from `parseChunk`. Adjust the file accordingly.
