package com.apex.engine.tools.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTest {

    @Test
    void parsesEmptyObject() {
        assertThat(Json.parseObject("{}")).isEmpty();
    }

    @Test
    void parsesString() {
        assertThat(Json.parseObject("{\"a\":\"b\"}")).containsEntry("a", "b");
    }

    @Test
    void parsesIntegralNumberAsLong() {
        assertThat(Json.parseObject("{\"n\":42}")).containsEntry("n", 42L);
    }

    @Test
    void parsesFractionalNumberAsDouble() {
        assertThat(Json.parseObject("{\"f\":3.14}")).containsEntry("f", 3.14d);
    }

    @Test
    void parsesBooleans() {
        assertThat(Json.parseObject("{\"t\":true}")).containsEntry("t", true);
        assertThat(Json.parseObject("{\"t\":false}")).containsEntry("t", false);
    }

    @Test
    void parsesNull() {
        Map<String, Object> parsed = Json.parseObject("{\"t\":null}");

        assertThat(parsed).containsKey("t");
        assertThat(parsed.get("t")).isNull();
    }

    @Test
    void parsesArrayOfStrings() {
        assertThat(Json.parseObject("{\"a\":[\"x\",\"y\"]}"))
                .containsEntry("a", List.of("x", "y"));
    }

    @Test
    void parsesNestedObject() {
        assertThat(Json.parseObject("{\"a\":{\"b\":\"c\"}}"))
                .containsEntry("a", Map.of("b", "c"));
    }

    @Test
    void handlesEscapedQuote() {
        assertThat(Json.parseObject("{\"s\":\"a\\\"b\"}"))
                .containsEntry("s", "a\"b");
    }

    @Test
    void handlesEscapedNewline() {
        assertThat(Json.parseObject("{\"s\":\"a\\nb\"}"))
                .containsEntry("s", "a\nb");
    }

    @Test
    void rejectsMalformedInput() {
        assertThatThrownBy(() -> Json.parseObject(""))
                .isInstanceOf(ToolException.class);
        assertThatThrownBy(() -> Json.parseObject("{"))
                .isInstanceOf(ToolException.class);
        assertThatThrownBy(() -> Json.parseObject("}"))
                .isInstanceOf(ToolException.class);
        assertThatThrownBy(() -> Json.parseObject("{\"a\""))
                .isInstanceOf(ToolException.class);
        assertThatThrownBy(() -> Json.parseObject("{\"a\":}"))
                .isInstanceOf(ToolException.class);
    }

    @Test
    void rejectsTrailingContent() {
        assertThatThrownBy(() -> Json.parseObject("{} {}"))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("trailing content");
    }
}
