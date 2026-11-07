package com.apex.engine.llm.http;

import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleProviderChatTest {

    private static final String OK_BODY =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"hello\"}}]}";

    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void happyPathReturnsContent() throws Exception {
        OpenAiCompatibleProvider provider = providerReturning(200, OK_BODY);

        ChatResponse response = provider.chat(request("model-x"));

        assertThat(response.content()).isEqualTo("hello");
    }

    @Test
    void blankModelIdUsesDefaultModel() throws Exception {
        OpenAiCompatibleProvider provider = providerReturning(200, OK_BODY);

        provider.chat(request(""));

        assertThat(lastRequestBody.get()).contains("\"model\":\"test-default\"");
    }

    @Test
    void unauthorizedMapsToAuthCategory() throws Exception {
        OpenAiCompatibleProvider provider = providerReturning(401, "nope");

        assertThatThrownBy(() -> provider.chat(request("m")))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).category())
                        .isEqualTo("auth"));
    }

    @Test
    void rateLimitMapsToRateLimitCategory() throws Exception {
        OpenAiCompatibleProvider provider = providerReturning(429, "slow down");

        assertThatThrownBy(() -> provider.chat(request("m")))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).category())
                        .isEqualTo("rate_limit"));
    }

    @Test
    void serverErrorMapsToServerCategory() throws Exception {
        OpenAiCompatibleProvider provider = providerReturning(500, "boom");

        assertThatThrownBy(() -> provider.chat(request("m")))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).category())
                        .isEqualTo("server"));
    }

    private static ChatRequest request(String modelId) {
        return new ChatRequest(modelId, List.of("hello"));
    }

    private OpenAiCompatibleProvider providerReturning(int status, String body)
            throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            lastRequestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        ProviderConfig config = new ProviderConfig(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "test-key", "test-default", Duration.ofSeconds(5));
        return new OpenAiCompatibleProvider(config);
    }
}
