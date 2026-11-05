package com.apex.engine.planner;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlackboardTest {

    @Test
    void emptyHasEmptySnapshot() {
        assertThat(Blackboard.empty().snapshot()).isEmpty();
    }

    @Test
    void getStringIsEmptyForMissingKey() {
        assertThat(Blackboard.empty().getString("absent")).isEmpty();
    }

    @Test
    void getStringReturnsPresentValue() {
        Blackboard state = Blackboard.empty().withString("k", "v");

        assertThat(state.getString("k")).contains("v");
    }

    @Test
    void getReturnsEmptyForWrongType() {
        Blackboard state = Blackboard.empty().withString("k", "v");

        assertThat(state.get("k", Integer.class)).isEmpty();
    }

    @Test
    void withReturnsNewInstanceAndLeavesOriginalUnchanged() {
        Blackboard original = Blackboard.empty();

        Blackboard updated = original.withString("k", "v");

        assertThat(updated).isNotSameAs(original);
        assertThat(original.snapshot()).isEmpty();
        assertThat(updated.getString("k")).contains("v");
    }

    @Test
    void snapshotIsUnmodifiable() {
        Map<String, Object> snapshot =
                Blackboard.empty().withString("k", "v").snapshot();

        assertThatThrownBy(() -> snapshot.put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
