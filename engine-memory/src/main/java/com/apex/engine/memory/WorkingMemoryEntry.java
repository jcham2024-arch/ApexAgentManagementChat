package com.apex.engine.memory;

import java.time.Instant;
import java.util.Objects;

/**
 * A single entry in working memory. {@code role} names the speaker or
 * category, using the same conventions as the runner's history
 * prefixes: {@code "system"}, {@code "task"}, {@code "assistant"},
 * {@code "tool_result"}, {@code "error"}. The compactor (S4.5) uses
 * role to identify SYSTEM messages and tool-call/result pairs.
 *
 * <p>{@code at} is the wall-clock time of the entry; it is not used
 * for query scoring in S4.1, but future phases may weight recent
 * entries more heavily.
 */
public record WorkingMemoryEntry(String role, String content, Instant at) {
    public WorkingMemoryEntry {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(at, "at");
        if (role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }
}
