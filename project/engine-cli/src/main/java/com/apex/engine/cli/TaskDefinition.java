package com.apex.engine.cli;

import java.util.List;
import java.util.Objects;

/**
 * A parsed and validated task file. Immutable. Lists are defensively
 * copied; strings are non-null. {@code modelRoute} may be empty (the
 * provider's default model is used).
 *
 * <p>The task schema is documented in {@code engine-cli/README.md}.
 */
public record TaskDefinition(
        String taskId,
        String description,
        String instructions,
        int maxSteps,
        String modelRoute,
        List<String> tools,
        List<String> readFirst,
        List<String> outputFiles,
        List<String> assertions
) {
    public TaskDefinition {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(instructions, "instructions");
        Objects.requireNonNull(modelRoute, "modelRoute");
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (instructions.isBlank()) {
            throw new IllegalArgumentException("instructions must not be blank");
        }
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps must be positive");
        }
        tools = List.copyOf(tools);
        if (tools.isEmpty()) {
            throw new IllegalArgumentException("tools must not be empty");
        }
        readFirst = List.copyOf(readFirst);
        outputFiles = List.copyOf(outputFiles);
        assertions = List.copyOf(assertions);
    }
}
