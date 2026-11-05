package com.apex.engine.llm.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseLineParserTest {

    @Test
    void extractsJsonPayload() {
        assertThat(SseLineParser.extractData("data: {\"a\":1}"))
                .contains("{\"a\":1}");
    }

    @Test
    void doneMarkerIsDetectedAndNotReturnedAsData() {
        assertThat(SseLineParser.isDoneMarker("data: [DONE]")).isTrue();
        assertThat(SseLineParser.extractData("data: [DONE]")).isEmpty();
    }

    @Test
    void blankLineReturnsEmpty() {
        assertThat(SseLineParser.extractData("")).isEmpty();
        assertThat(SseLineParser.extractData("data:   ")).isEmpty();
        assertThat(SseLineParser.isDoneMarker("")).isFalse();
    }

    @Test
    void commentLineReturnsEmpty() {
        assertThat(SseLineParser.extractData(": keep-alive")).isEmpty();
        assertThat(SseLineParser.extractData("event: message")).isEmpty();
    }

    @Test
    void nullReturnsEmpty() {
        assertThat(SseLineParser.extractData(null)).isEmpty();
        assertThat(SseLineParser.isDoneMarker(null)).isFalse();
    }

    @Test
    void doneMarkerAllowsSurroundingWhitespace() {
        assertThat(SseLineParser.isDoneMarker("  data: [DONE]  ")).isTrue();
    }
}
