package com.apex.engine.llm.http;

import com.apex.engine.core.port.model.ChatChunk;
import com.apex.engine.core.port.model.ChatResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiProtocolTest {

    private static final ProviderConfig CONFIG = new ProviderConfig(
            "https://example.test/v1", "secret", "default-model",
            Duration.ofSeconds(5));

    @Test
    void buildBodyCarriesModelStreamFlagAndPrompt() {
        String body = OpenAiProtocol.buildBody("m", List.of("a", "b"), false);

        assertThat(JsonParser.parseObject(body))
                .containsEntry("model", "m")
                .containsEntry("stream", false);
        assertThat(body).contains("\"content\":\"a\\n\\nb\"");
    }

    @Test
    void buildBodyEscapesQuotesAndNewlines() {
        String body = OpenAiProtocol.buildBody("m\"x\ny", List.of("p\\q"), true);

        assertThat(body).contains("\\\"").contains("\\n").contains("\\\\");
        assertThat(JsonParser.parseObject(body)).containsEntry("model", "m\"x\ny");
    }

    @Test
    void buildRequestUsesBaseUrlAndBearerToken() {
        var request = OpenAiProtocol.buildRequest(CONFIG, "{}", true);

        assertThat(request.uri().toString())
                .isEqualTo("https://example.test/v1/chat/completions");
        assertThat(request.headers().firstValue("Authorization"))
                .contains("Bearer secret");
        assertThat(request.headers().firstValue("Accept"))
                .contains("text/event-stream");
    }

    @Test
    void parseChatResponseExtractsContent() {
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"hello\"}}]}";

        ChatResponse response = OpenAiProtocol.parseChatResponse(body);

        assertThat(response.content()).isEqualTo("hello");
    }

    @Test
    void parseChatResponseThrowsWithoutChoices() {
        assertThatThrownBy(() -> OpenAiProtocol.parseChatResponse("{}"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("missing choices");
    }

    @Test
    void parseChatResponseThrowsWithoutMessage() {
        assertThatThrownBy(() -> OpenAiProtocol.parseChatResponse(
                "{\"choices\":[{}]}"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("missing message");
    }

    @Test
    void parseChunkReturnsDeltaAndFinishReason() {
        Optional<ChatChunk> chunk = OpenAiProtocol.parseChunk(
                "{\"choices\":[{\"delta\":{\"content\":\"Hi\"},"
                + "\"finish_reason\":null}]}");

        assertThat(chunk).contains(new ChatChunk("Hi", false, null));
    }

    @Test
    void parseChunkReturnsTerminalChunkFromFinishReason() {
        Optional<ChatChunk> chunk = OpenAiProtocol.parseChunk(
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}");

        assertThat(chunk).contains(new ChatChunk("", true, "length"));
    }

    @Test
    void parseChunkIsEmptyWhenNoContentAndNoFinish() {
        assertThat(OpenAiProtocol.parseChunk(
                "{\"choices\":[{\"delta\":{},\"finish_reason\":null}]}"))
                .isEmpty();
        assertThat(OpenAiProtocol.parseChunk("{\"usage\":{}}")).isEmpty();
    }

    @Test
    void mapHttpErrorClassifiesStatus() {
        assertThat(OpenAiProtocol.mapHttpError(401, "x").category()).isEqualTo("auth");
        assertThat(OpenAiProtocol.mapHttpError(403, "x").category()).isEqualTo("auth");
        assertThat(OpenAiProtocol.mapHttpError(429, "x").category())
                .isEqualTo("rate_limit");
        assertThat(OpenAiProtocol.mapHttpError(500, "x").category())
                .isEqualTo("server");
        assertThat(OpenAiProtocol.mapHttpError(404, "x").category())
                .isEqualTo("client");
    }

    @Test
    void mapHttpErrorTruncatesLongBody() {
        String longBody = "y".repeat(600);

        LlmException error = OpenAiProtocol.mapHttpError(500, longBody);

        assertThat(error.getMessage()).contains("HTTP 500");
        assertThat(error.getMessage().length()).isLessThan(600);
    }
}
