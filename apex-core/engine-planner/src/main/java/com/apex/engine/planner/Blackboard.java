package com.apex.engine.planner;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of the planner's world state. Every mutating
 * method returns a new instance. Keys are strings; values are typed
 * and retrieved via {@link #get(String, Class)}.
 */
public interface Blackboard {
    Optional<String> getString(String key);
    <T> Optional<T> get(String key, Class<T> type);
    Blackboard withString(String key, String value);
    Blackboard with(String key, Object value);
    Map<String, Object> snapshot();
}
