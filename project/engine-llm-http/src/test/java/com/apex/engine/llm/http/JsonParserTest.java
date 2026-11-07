package com.apex.engine.llm.http;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Same coverage as the S2.3 JsonTest, plus the exception-type check. */
class JsonParserTest {

    @Test
    void parsesEmptyObject() {
        assertThat(JsonParser.parseObject("{}")).isEmpty();
    }

    @Test
    void parsesString() {
        assertThat(JsonParser.parseObject("{\"a\":\"b\"}")).containsEntry("a", "b");
    }

    @Test
    void parsesIntegralNumberAsLong() {
        assertThat(JsonParser.parseObject("{\"n\":42}")).containsEntry("n", 42L);
    }

    @Test
    void parsesFractionalNumberAsDouble() {
        assertThat(JsonParser.parseObject("{\"f\":3.14}")).containsEntry("f", 3.14d);
    }

    @Test
    void parsesBooleansAndNull() {
        assertThat(JsonParser.parseObject("{\"t\":true}")).containsEntry("t", true);
        assertThat(JsonParser.parseObject("{\"t\":false}")).containsEntry("t", false);
        assertThat(JsonParser.parseObject("{\"t\":null}")).containsKey("t");
    }

    @Test
    void parsesArrayOfStrings() {
        assertThat(JsonParser.parseObject("{\"a\":[\"x\",\"y\"]}"))
                .containsEntry("a", List.of("x", "y"));
    }

    @Test
    void parsesNestedObject() {
        assertThat(JsonParser.parseObject("{\"a\":{\"b\":{\"c\":1}}}"))
                .containsEntry("a", Map.of("b", Map.of("c", 1L)));
    }

    @Test
    void handlesEscapes() {
        assertThat(JsonParser.parseObject("{\"s\":\"a\\\"b\"}"))
                .containsEntry("s", "a\"b");
        assertThat(JsonParser.parseObject("{\"s\":\"a\\nb\"}"))
                .containsEntry("s", "a\nb");
    }

    @Test
    void rejectsMalformedInput() {
        assertThatThrownBy(() -> JsonParser.parseObject(""))
                .isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> JsonParser.parseObject("{"))
                .isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> JsonParser.parseObject("{\"a\":}"))
                .isInstanceOf(LlmException.class);
    }

    @Test
    void rejectsTrailingContent() {
        assertThatThrownBy(() -> JsonParser.parseObject("{} {}"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("trailing content");
    }

    @Test
    void throwsLlmExceptionNotAToolException() {
        assertThatThrownBy(() -> JsonParser.parseObject("nope"))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> {
                    assertThat(e.getClass().getName())
                            .isEqualTo("com.apex.engine.llm.http.LlmException");
                    assertThat(((LlmException) e).category()).isEqualTo("json");
                });
    }
}
