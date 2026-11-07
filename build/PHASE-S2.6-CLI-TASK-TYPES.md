# PHASE-S2.6-CLI-TASK-TYPES.md — Session Package, Part 2 of 6

**Version:** 1.0 · **Sub-phase of S2** · **Part 2 of 6:** §4.2–§4.4, TaskLoadException, TaskDefinition, TaskLoader.
Parts: SESSION · TASK-TYPES · FIELDS · RUNNER-MAIN · METADATA ·
TESTS-CRITERIA, each `PHASE-S2.6-CLI-<part>.md` (`AGENT_RULES.md` §2.13).

---
### 4.2 `engine-cli/src/main/java/com/apex/engine/cli/TaskLoadException.java`

```java
package com.apex.engine.cli;

/**
 * Raised when a task file cannot be read, parsed, or validated. The
 * message names the field or structural problem. Categories:
 * {@code io}, {@code yaml}, {@code schema}.
 */
public final class TaskLoadException extends RuntimeException {
    private final String category;

    public TaskLoadException(String category, String message) {
        super(message);
        this.category = category;
    }

    public TaskLoadException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
```

### 4.3 `TaskDefinition.java`

```java
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
```

### 4.4 `TaskLoader.java`

```java
package com.apex.engine.cli;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Reads a task file from disk and produces a validated
 * {@link TaskDefinition}. Uses SnakeYAML's safe constructor — no
 * arbitrary class loading. All errors are wrapped in
 * {@link TaskLoadException}.
 */
public final class TaskLoader {

    public TaskDefinition load(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.exists(file)) {
            throw new TaskLoadException("io",
                    "task file not found: " + file);
        }
        if (!Files.isRegularFile(file)) {
            throw new TaskLoadException("io",
                    "not a regular file: " + file);
        }
        Map<String, Object> root = parse(file);
        return TaskFields.fromYaml(root);
    }

    private Map<String, Object> parse(Path file) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (InputStream in = Files.newInputStream(file)) {
            Object parsed = yaml.load(in);
            if (parsed == null) {
                throw new TaskLoadException("schema", "task file is empty");
            }
            if (!(parsed instanceof Map<?, ?> raw)) {
                throw new TaskLoadException("schema",
                        "task file root must be a mapping");
            }
            return TaskFields.asStringKeyedMap(raw);
        } catch (IOException e) {
            throw new TaskLoadException("io", e.getMessage(), e);
        } catch (YAMLException e) {
            throw new TaskLoadException("yaml", e.getMessage(), e);
        }
    }
}
```
