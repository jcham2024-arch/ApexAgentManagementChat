package com.apex.engine.planner;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link Blackboard} backed by an unmodifiable map. Equality is
 * value-based (record semantics on the wrapped map).
 */
public record ImmutableBlackboard(Map<String, Object> facts) implements Blackboard {
    public ImmutableBlackboard {
        Objects.requireNonNull(facts, "facts");
        facts = Map.copyOf(facts);
    }

    public static ImmutableBlackboard empty() {
        return new ImmutableBlackboard(Map.of());
    }

    @Override
    public Optional<String> getString(String key) {
        return get(key, String.class);
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object v = facts.get(key);
        return v != null && type.isInstance(v) ? Optional.of(type.cast(v)) : Optional.empty();
    }

    @Override
    public Blackboard withString(String key, String value) {
        return with(key, value);
    }

    @Override
    public Blackboard with(String key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Map<String, Object> next = new HashMap<>(facts);
        next.put(key, value);
        return new ImmutableBlackboard(next);
    }

    @Override
    public Map<String, Object> snapshot() {
        return facts;
    }
}
