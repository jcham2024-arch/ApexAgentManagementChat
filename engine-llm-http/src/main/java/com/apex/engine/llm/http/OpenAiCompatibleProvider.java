package com.apex.engine.llm.http;

import com.apex.engine.core.concurrent.CancellationToken;
import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.model.ChatChunk;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Default {@link ChatModelPort} for OpenAI-compatible providers
 * (OpenRouter, NVIDIA NIM, DeepSeek, Groq, OpenAI, Gemini-compat, and
 * any other endpoint that speaks the same contract).
 *
 * <p>Streaming cancellation is checked between chunks. If the provider
 * is slow to send the next chunk, cancellation is delayed until that
 * chunk arrives.
 */
public final class OpenAiCompatibleProvider implements ChatModelPort {

    private final ProviderConfig config;
    private final HttpClient client;

    public OpenAiCompatibleProvider(ProviderConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public OpenAiCompatibleProvider(ProviderConfig config, HttpClient client) {
        this.config = Objects.requireNonNull(config, "config");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String model = modelFor(request);
        String body = OpenAiProtocol.buildBody(model, request.promptParts(), false);
        HttpRequest httpReq = OpenAiProtocol.buildRequest(config, body, false);
        try {
            HttpResponse<String> resp = client.send(httpReq,
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw OpenAiProtocol.mapHttpError(resp.statusCode(), resp.body());
            }
            return OpenAiProtocol.parseChatResponse(resp.body());
        } catch (IOException e) {
            throw new LlmException("network", e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("cancelled", "interrupted", e);
        }
    }

    @Override
    public void stream(ChatRequest request, CancellationToken token,
                       Consumer<ChatChunk> onChunk) {
        String model = modelFor(request);
        String body = OpenAiProtocol.buildBody(model, request.promptParts(), true);
        HttpRequest httpReq = OpenAiProtocol.buildRequest(config, body, true);
        try {
            HttpResponse<InputStream> resp = client.send(httpReq,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 400) {
                String errBody = readAll(resp.body());
                throw OpenAiProtocol.mapHttpError(resp.statusCode(), errBody);
            }
            consumeSse(resp.body(), token, onChunk);
        } catch (IOException e) {
            throw new LlmException("stream_io", e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("cancelled", "interrupted", e);
        }
    }

    private void consumeSse(InputStream stream, CancellationToken token,
                            Consumer<ChatChunk> onChunk) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (token.isCancelled()) return;
                if (SseLineParser.isDoneMarker(line)) {
                    onChunk.accept(new ChatChunk("", true, "stop"));
                    return;
                }
                Optional<String> data = SseLineParser.extractData(line);
                if (data.isEmpty()) continue;
                Optional<ChatChunk> chunk =
                        OpenAiProtocol.parseChunk(data.get());
                chunk.ifPresent(onChunk);
            }
        }
    }

    private String modelFor(ChatRequest request) {
        String m = request.modelId();
        return (m == null || m.isBlank()) ? config.defaultModel() : m;
    }

    private static String readAll(InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<unreadable>";
        }
    }
}
