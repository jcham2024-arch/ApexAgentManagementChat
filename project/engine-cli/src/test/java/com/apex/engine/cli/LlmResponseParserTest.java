package com.apex.engine.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmResponseParserTest {

    LlmResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new LlmResponseParser();
    }

    @Test
    void parsesActionWithArguments() {
        Decision decision = parser.parse(
                "{\"action\":\"read_file\",\"arguments\":{\"path\":\"a\"}}");

        assertThat(decision).isEqualTo(
                new Decision.Action("read_file", "{\"path\":\"a\"}"));
    }

    @Test
    void parsesDone() {
        assertThat(parser.parse("{\"done\":true,\"summary\":\"ok\"}"))
                .isEqualTo(new Decision.Done("ok"));
    }

    @Test
    void rejectsDoneFalseWithoutAction() {
        assertThatThrownBy(() -> parser.parse("{\"done\":false}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing 'action' or 'done'");
    }

    @Test
    void rejectsNonJson() {
        assertThatThrownBy(() -> parser.parse("not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no JSON object");
    }

    @Test
    void defaultsMissingArgumentsToEmptyObject() {
        assertThat(parser.parse("{\"action\":\"x\"}"))
                .isEqualTo(new Decision.Action("x", "{}"));
    }

    @Test
    void parsesJsonAfterProse() {
        assertThat(parser.parse("Sure. {\"done\":true,\"summary\":\"ok\"}"))
                .isEqualTo(new Decision.Done("ok"));
    }

    @Test
    void parsesJsonInsideCodeFence() {
        String fenced = "```json\n{\"done\":true,\"summary\":\"ok\"}\n```";

        assertThat(parser.parse(fenced)).isEqualTo(new Decision.Done("ok"));
    }

    @Test
    void rejectsUnbalancedBraces() {
        assertThatThrownBy(() -> parser.parse("{\"action\":\"x\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unbalanced braces");
    }

    @Test
    void treatsBraceInsideStringAsContent() {
        Decision decision = parser.parse(
                "{\"action\":\"read_file\",\"arguments\":{\"path\":\"a}b\"}}");

        assertThat(decision).isEqualTo(
                new Decision.Action("read_file", "{\"path\":\"a}b\"}"));
    }

    @Test
    void serializesNestedArguments() {
        Decision decision = parser.parse(
                "{\"action\":\"x\",\"arguments\":{\"a\":{\"b\":\"c\"},\"n\":2}}");

        assertThat(decision).isEqualTo(
                new Decision.Action("x", "{\"a\":{\"b\":\"c\"},\"n\":2}"));
    }

    @Test
    void serializesScalarArgumentKinds() {
        Decision decision = parser.parse(
                "{\"action\":\"x\",\"arguments\":{\"n\":1,\"b\":true,\"s\":\"t\"}}");

        assertThat(decision).isEqualTo(
                new Decision.Action("x", "{\"n\":1,\"b\":true,\"s\":\"t\"}"));
    }
}
