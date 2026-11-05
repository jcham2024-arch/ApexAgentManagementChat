package com.apex.engine.core.port.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ChatChunkTest {

    @Test
    void acceptsNonTerminalChunkWithoutFinishReason() {
        ChatChunk chunk = new ChatChunk("x", false, null);

        assertThat(chunk.delta()).isEqualTo("x");
        assertThat(chunk.done()).isFalse();
        assertThat(chunk.finishReason()).isNull();
    }

    @Test
    void acceptsTerminalChunkWithEmptyDelta() {
        ChatChunk chunk = new ChatChunk("", true, "stop");

        assertThat(chunk.delta()).isEmpty();
        assertThat(chunk.done()).isTrue();
        assertThat(chunk.finishReason()).isEqualTo("stop");
    }

    @Test
    void rejectsNullDelta() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ChatChunk(null, false, null))
                .withMessage("delta");
    }

    @Test
    void rejectsTerminalChunkWithoutFinishReason() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChatChunk("x", true, null))
                .withMessage("finishReason required when done=true");
    }

    @Test
    void equalityIsValueBased() {
        ChatChunk first = new ChatChunk("x", false, null);
        ChatChunk second = new ChatChunk("x", false, null);

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(new ChatChunk("y", false, null));
        assertThat(first).isNotEqualTo(new ChatChunk("x", true, "stop"));
    }
}
