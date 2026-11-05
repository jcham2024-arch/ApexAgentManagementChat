package com.apex.engine.llm.http;

import com.apex.engine.planner.concurrent.CancellationToken;
import com.apex.engine.core.port.model.ChatChunk;
import com.apex.engine.core.port.model.ChatRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleProviderStreamTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void streamsTwoDeltasThenDoneMarker() throws Exception {
        OpenAiCompatibleProvider provider = providerStreaming(List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"},"
                        + "\"finish_reason\":null}]}",
                "",
                "data: {\"choices\":[{\"delta\":{\"content\":\" world\"},"
                        + "\"finish_reason\":null}]}",
                "",
                "data: [DONE]"));

        List<ChatChunk> chunks = new ArrayList<>();
        provider.stream(request(), new CancellationToken(), chunks::add);

        assertThat(chunks).containsExactly(
                new ChatChunk("Hello", false, null),
                new ChatChunk(" world", false, null),
                new ChatChunk("", true, "stop"));
    }

    @Test
    void finishesOnFinishReasonWithoutDoneMarker() throws Exception {
        OpenAiCompatibleProvider provider = providerStreaming(List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"},"
                        + "\"finish_reason\":null}]}",
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}"));

        List<ChatChunk> chunks = new ArrayList<>();
        provider.stream(request(), new CancellationToken(), chunks::add);

        assertThat(chunks).containsExactly(
                new ChatChunk("Hi", false, null),
                new ChatChunk("", true, "length"));
    }

    @Test
    void cancellationStopsBeforeAllChunksArrive() throws Exception {
        int total = 30;
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            lines.add("data: {\"choices\":[{\"delta\":{\"content\":\"c" + i
                    + "\"},\"finish_reason\":null}]}");
        }
        OpenAiCompatibleProvider provider = providerStreaming(lines, 20L);

        CancellationToken token = new CancellationToken();
        Thread canceller = new Thread(() -> {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            token.cancel();
        });
        canceller.start();

        List<ChatChunk> chunks = new ArrayList<>();
        provider.stream(request(), token, chunks::add);
        canceller.join(1000L);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isLessThan(total);
    }

    @Test
    void streamingErrorStatusMapsToServerCategory() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = "boom".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        assertThatThrownBy(() -> provider().stream(request(), new CancellationToken(),
                chunk -> { }))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).category())
                        .isEqualTo("server"));
    }

    private static ChatRequest request() {
        return new ChatRequest("test-model", List.of("hello"));
    }

    private OpenAiCompatibleProvider providerStreaming(List<String> lines)
            throws Exception {
        return providerStreaming(lines, 0L);
    }

    private OpenAiCompatibleProvider providerStreaming(List<String> lines,
                                                       long delayMs)
            throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions",
                exchange -> writeSse(exchange, lines, delayMs));
        server.start();
        return provider();
    }

    private OpenAiCompatibleProvider provider() {
        ProviderConfig config = new ProviderConfig(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "test-key", "test-default", Duration.ofSeconds(5));
        return new OpenAiCompatibleProvider(config);
    }

    private static void writeSse(HttpExchange exchange, List<String> lines,
                                long delayMs) {
        try {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (String line : lines) {
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    if (delayMs > 0) {
                        Thread.sleep(delayMs);
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            // The client stopped reading (cancellation) or the test ended.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
