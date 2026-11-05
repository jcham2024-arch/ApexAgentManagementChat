package com.apex.engine.llm.http;

import com.apex.engine.core.port.model.ChatChunk;
import com.apex.engine.core.port.model.ChatResponse;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Building blocks for the OpenAI chat-completions HTTP contract. Not a
 * port implementation. Keeps {@link OpenAiCompatibleProvider} under the
 * 200-line limit.
 *
 * <p>{@link #parseChunk(String)} returns {@link Optional}: a chunk that
 * carries neither content nor a finish reason is not emitted at all,
 * which is cleaner than fabricating a terminal chunk (S2.4 5.6 note).
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
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> rawMessage)) {
            throw new LlmException("parse", "response missing message");
        }
        Object content = rawMessage.get("content");
        return new ChatResponse(content == null ? "" : content.toString());
    }

    /**
     * Parses one SSE data payload. Empty when the payload carries
     * neither delta content nor a finish reason.
     */
    static Optional<ChatChunk> parseChunk(String dataJson) {
        Map<String, Object> root = JsonParser.parseObject(dataJson);
        Object rawChoices = root.get("choices");
        if (!(rawChoices instanceof List<?> choices) || choices.isEmpty()) {
            return Optional.empty();
        }
        if (!(choices.get(0) instanceof Map<?, ?> choice)) {
            return Optional.empty();
        }
        String content = "";
        Object rawDelta = choice.get("delta");
        if (rawDelta instanceof Map<?, ?> delta && delta.get("content") != null) {
            content = delta.get("content").toString();
        }
        Object finish = choice.get("finish_reason");
        boolean done = finish != null;
        if (content.isEmpty() && !done) {
            return Optional.empty();
        }
        return Optional.of(new ChatChunk(content, done,
                done ? finish.toString() : null));
    }

    static LlmException mapHttpError(int status, String body) {
        String category;
        if (status == 401 || status == 403) category = "auth";
        else if (status == 429) category = "rate_limit";
        else if (status >= 500) category = "server";
        else category = "client";
        String snippet = body == null ? "" : body;
        if (snippet.length() > 500) snippet = snippet.substring(0, 500);
        return new LlmException(category, "HTTP " + status + ": " + snippet);
    }

    private static Map<String, Object> firstChoice(Map<String, Object> root) {
        Object rawChoices = root.get("choices");
        if (!(rawChoices instanceof List<?> choices) || choices.isEmpty()) {
            throw new LlmException("parse", "response missing choices");
        }
        if (!(choices.get(0) instanceof Map<?, ?> choice)) {
            throw new LlmException("parse", "response choice is not an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) choice;
        return typed;
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
