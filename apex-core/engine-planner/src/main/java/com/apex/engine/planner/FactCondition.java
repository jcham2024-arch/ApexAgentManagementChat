package com.apex.engine.planner;

import java.util.Objects;

/**
 * A condition satisfied when the blackboard's value at {@code key}
 * equals {@code value}.
 */
public record FactCondition(String key, String value) implements Condition {
    public FactCondition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }

    @Override
    public boolean evaluate(Blackboard state) {
        return state.getString(key).map(value::equals).orElse(false);
    }

    @Override
    public String describe() {
        return key + "=" + value;
    }
}
