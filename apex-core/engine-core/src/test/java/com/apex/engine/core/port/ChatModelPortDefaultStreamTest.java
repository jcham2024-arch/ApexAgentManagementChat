package com.apex.engine.core.port;

import com.apex.engine.planner.concurrent.CancellationToken;
import com.apex.engine.core.port.model.ChatChunk;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatModelPortDefaultStreamTest {

    private static final ChatModelPort LAMBDA_PORT =
            request -> new ChatResponse("hi");

    private static ChatRequest request() {
        return new ChatRequest("m", List.of("prompt"));
    }

    @Test
    void lambdaImplementationStillCompilesBecausePortStaysFunctional() {
        assertThat(LAMBDA_PORT.chat(request()).content()).isEqualTo("hi");
    }

    @Test
    void defaultStreamEmitsOneTerminalChunk() {
        List<ChatChunk> chunks = new ArrayList<>();

        LAMBDA_PORT.stream(request(), new CancellationToken(), chunks::add);

        assertThat(chunks).containsExactly(new ChatChunk("hi", true, "stop"));
    }

    @Test
    void defaultStreamEmitsNothingWhenAlreadyCancelled() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        List<ChatChunk> chunks = new ArrayList<>();

        LAMBDA_PORT.stream(request(), token, chunks::add);

        assertThat(chunks).isEmpty();
    }
}
