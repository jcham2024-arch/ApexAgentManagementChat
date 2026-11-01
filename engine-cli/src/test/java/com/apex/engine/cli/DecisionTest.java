package com.apex.engine.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DecisionTest {

    @Test
    void actionRejectsBlankToolName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Decision.Action("  ", "{}"))
                .withMessage("toolName blank");
    }

    @Test
    void actionRejectsNullArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Decision.Action("read_file", null));
    }

    @Test
    void doneRejectsNullSummary() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Decision.Done(null));
    }

    @Test
    void actionEqualityIsValueBased() {
        Decision.Action first = new Decision.Action("read_file", "{\"path\":\"a\"}");
        Decision.Action second = new Decision.Action("read_file", "{\"path\":\"a\"}");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(new Decision.Action("read_file", "{}"));
    }

    @Test
    void doneEqualityIsValueBased() {
        assertThat(new Decision.Done("ok")).isEqualTo(new Decision.Done("ok"));
        assertThat(new Decision.Done("ok")).isNotEqualTo(new Decision.Done("no"));
    }
}
