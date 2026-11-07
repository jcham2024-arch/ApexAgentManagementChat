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
