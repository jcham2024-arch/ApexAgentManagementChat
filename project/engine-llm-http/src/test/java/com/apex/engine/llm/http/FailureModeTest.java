package com.apex.engine.llm.http;

import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.planner.concurrent.CancellationToken;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Failure-mode tests for the LLM HTTP adapter. The engine must classify
 * every provider failure into a predictable category so the plan loop
 * can decide whether to retry, escalate, or fail fast.
 */
class FailureModeTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void malformedJsonIsParseError() throws Exception {
        server = serverReturning(200, "not json at all");
        OpenAiCompatibleProvider provider = provider();

        assertThatThrownBy(() -> provider.chat(request("m")))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).category())
                        .isEqualTo("json"));
    }

    @Test
    void missingChoicesIsParseError() throws Exception {
        server = serverReturning(200, "{\"foo\":\"bar\"}");
        OpenAiCompatibleProvider provider = provider();

        assertThatThrownBy(() -> provider.chat(request("m")))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).category())
                        .isEqualTo("parse"));
    }

    @Test
    void emptyBodyIsParseError() throws Exception {
        server = serverReturning(200, "");
        OpenAiCompatibleProvider provider = provider();

        assertThatThrownBy(() -> provider.chat(request("m")))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).category())
                        .isEqualTo("json"));
    }

    @Test
    void connectionRefusedIsNetworkError() throws Exception {
        // No server listening; connection must fail fast.
        ProviderConfig config = new ProviderConfig(
                "http://127.0.0.1:1/v1", "test-key", "test-default",
                Duration.ofSeconds(2));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(config, client);

        assertThatThrownBy(() -> provider.chat(request("m")))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).category())
                        .isEqualTo("network"));
    }

    @Test
    void malformedJsonInChunkIsParseError() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = "data: not json\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        ProviderConfig config = new ProviderConfig(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "test-key", "test-default", Duration.ofSeconds(5));
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(config);

        assertThatThrownBy(() -> {
            provider.stream(request("m"), new CancellationToken(), chunk -> {});
        }).isInstanceOf(LlmException.class)
          .satisfies(e -> assertThat(((LlmException) e).category())
                  .isEqualTo("json"));
    }

    private static ChatRequest request(String modelId) {
        return new ChatRequest(modelId, List.of("hello"));
    }

    private OpenAiCompatibleProvider provider() {
        return new OpenAiCompatibleProvider(
                new ProviderConfig(
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "test-key", "test-default", Duration.ofSeconds(5)));
    }

    private HttpServer serverReturning(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
