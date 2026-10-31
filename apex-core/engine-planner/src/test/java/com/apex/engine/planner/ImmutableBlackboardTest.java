package com.apex.engine.planner;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImmutableBlackboardTest {

    @Test
    void emptyHasEmptySnapshot() {
        assertThat(ImmutableBlackboard.empty().snapshot()).isEmpty();
    }

    @Test
    void getStringIsEmptyForMissingKey() {
        assertThat(ImmutableBlackboard.empty().getString("absent")).isEmpty();
    }

    @Test
    void getStringReturnsPresentValue() {
        Blackboard state = ImmutableBlackboard.empty().withString("k", "v");

        assertThat(state.getString("k")).contains("v");
    }

    @Test
    void getReturnsEmptyForWrongType() {
        Blackboard state = ImmutableBlackboard.empty().withString("k", "v");

        assertThat(state.get("k", Integer.class)).isEmpty();
    }

    @Test
    void withReturnsNewInstanceAndLeavesOriginalUnchanged() {
        Blackboard original = ImmutableBlackboard.empty();

        Blackboard updated = original.withString("k", "v");

        assertThat(updated).isNotSameAs(original);
        assertThat(original.snapshot()).isEmpty();
        assertThat(updated.getString("k")).contains("v");
    }

    @Test
    void snapshotIsUnmodifiable() {
        Map<String, Object> snapshot =
                ImmutableBlackboard.empty().withString("k", "v").snapshot();

        assertThatThrownBy(() -> snapshot.put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
