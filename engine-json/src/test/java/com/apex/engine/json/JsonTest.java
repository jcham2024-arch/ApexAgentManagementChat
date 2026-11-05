package com.apex.engine.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesEmptyObject() {
        assertThat(Json.parseObject("{}")).isEmpty();
    }

    @Test
    void parsesStringValue() {
        assertThat(Json.parseObject("{\"a\":\"b\"}")).containsEntry("a", "b");
    }

    @Test
    void parsesLongValue() {
        assertThat(Json.parseObject("{\"n\":42}")).containsEntry("n", 42L);
    }

    @Test
    void parsesDoubleValue() {
        assertThat(Json.parseObject("{\"f\":3.14}")).containsEntry("f", 3.14d);
    }

    @Test
    void parsesBooleanTrue() {
        assertThat(Json.parseObject("{\"t\":true}")).containsEntry("t", true);
    }

    @Test
    void parsesBooleanFalse() {
        assertThat(Json.parseObject("{\"t\":false}")).containsEntry("t", false);
    }

    @Test
    void parsesNullValue() {
        Map<String, Object> parsed = Json.parseObject("{\"t\":null}");
        assertThat(parsed).containsKey("t");
        assertThat(parsed.get("t")).isNull();
    }

    @Test
    void parsesArray() {
        assertThat(Json.parseObject("{\"a\":[\"x\",\"y\"]}"))
                .satisfies(map -> assertThat(map.get("a")).isEqualTo(List.of("x", "y")));
    }

    @Test
    void parsesNestedObject() {
        assertThat(Json.parseObject("{\"a\":{\"b\":{\"c\":1}}}"))
                .satisfies(map -> {
                    Map<String, Object> inner = (Map<String, Object>) map.get("a");
                    assertThat(inner).containsEntry("b", Map.of("c", 1L));
                });
    }

    @Test
    void parsesEscapedString() {
        assertThat(Json.parseObject("{\"s\":\"a\\\"b\"}")).containsEntry("s", "a\"b");
        assertThat(Json.parseObject("{\"s\":\"a\\nb\"}")).containsEntry("s", "a\nb");
    }

    @Test
    void rejectsEmptyInput() {
        assertThatThrownBy(() -> Json.parseObject(""))
                .isInstanceOf(JsonException.class);
    }

    @Test
    void rejectsUnterminatedObject() {
        assertThatThrownBy(() -> Json.parseObject("{"))
                .isInstanceOf(JsonException.class);
    }

    @Test
    void rejectsStrayCloseBrace() {
        assertThatThrownBy(() -> Json.parseObject("}"))
                .isInstanceOf(JsonException.class);
    }

    @Test
    void rejectsTruncatedObject() {
        assertThatThrownBy(() -> Json.parseObject("{\"a\""))
                .isInstanceOf(JsonException.class);
    }

    @Test
    void rejectsMissingColon() {
        assertThatThrownBy(() -> Json.parseObject("{\"a\"}"))
                .isInstanceOf(JsonException.class);
    }

    @Test
    void rejectsTrailingContent() {
        assertThatThrownBy(() -> Json.parseObject("{} {}"))
                .isInstanceOf(JsonException.class);
    }

    @Test
    void rejectsNonObject() {
        assertThatThrownBy(() -> Json.parseObject("nope"))
                .isInstanceOf(JsonException.class);
    }
}
