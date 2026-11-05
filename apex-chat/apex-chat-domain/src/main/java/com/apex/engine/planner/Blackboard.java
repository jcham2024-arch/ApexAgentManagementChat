package com.apex.engine.planner;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable key/value state passed through the plan loop. Every
 * mutating method returns a new instance.
 */
public record Blackboard(Map<String, Object> facts) {

    public Blackboard {
        Objects.requireNonNull(facts, "facts");
        facts = Map.copyOf(facts);
    }

    public static Blackboard empty() {
        return new Blackboard(Map.of());
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        return Optional.ofNullable(facts.get(key))
                .filter(type::isInstance)
                .map(type::cast);
    }

    public Optional<String> getString(String key) {
        return get(key, String.class);
    }

    public Map<String, Object> snapshot() {
        return facts;
    }

    public Blackboard with(String key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Map<String, Object> next = new HashMap<>(facts);
        next.put(key, value);
        return new Blackboard(next);
    }

    public Blackboard withString(String key, String value) {
        return with(key, value);
    }
}
