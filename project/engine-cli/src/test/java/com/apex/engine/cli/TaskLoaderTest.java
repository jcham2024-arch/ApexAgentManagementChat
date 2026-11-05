package com.apex.engine.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskLoaderTest {

    private static final String FULL_TASK = """
            task_id: sample
            description: Sample task
            instructions: |
              Do the thing.
              Then stop.
            max_steps: 5
            model_route: gpt-x
            tools: [read_file, write_file]
            inputs:
              read_first: [a.md, b.md]
            outputs:
              files: [out.md]
              assertions: ["file_exists:out.md"]
            """;

    @TempDir
    Path tempDir;

    TaskLoader loader;

    @BeforeEach
    void setUp() {
        loader = new TaskLoader();
    }

    @Test
    void loadsValidTask() {
        TaskDefinition task = loader.load(writeTask(FULL_TASK));

        assertThat(task.taskId()).isEqualTo("sample");
        assertThat(task.description()).isEqualTo("Sample task");
        assertThat(task.maxSteps()).isEqualTo(5);
        assertThat(task.modelRoute()).isEqualTo("gpt-x");
        assertThat(task.tools()).containsExactly("read_file", "write_file");
        assertThat(task.readFirst()).containsExactly("a.md", "b.md");
        assertThat(task.outputFiles()).containsExactly("out.md");
        assertThat(task.assertions()).containsExactly("file_exists:out.md");
    }

    @Test
    void multiLineInstructionsPreserved() {
        TaskDefinition task = loader.load(writeTask(FULL_TASK));

        assertThat(task.instructions())
                .contains("Do the thing.")
                .contains("Then stop.")
                .contains("\n");
    }

    @Test
    void optionalFieldsDefaultToEmpty() {
        TaskDefinition task = loader.load(writeTask("""
                task_id: minimal
                description: d
                instructions: i
                max_steps: 1
                tools: [read_file]
                """));

        assertThat(task.modelRoute()).isEmpty();
        assertThat(task.readFirst()).isEmpty();
        assertThat(task.outputFiles()).isEmpty();
        assertThat(task.assertions()).isEmpty();
    }

    @Test
    void nestedListsParsed() {
        TaskDefinition task = loader.load(writeTask("""
                task_id: nested
                description: d
                instructions: i
                max_steps: 2
                tools: [read_file]
                inputs:
                  read_first: [one.md]
                outputs:
                  files: [two.md, three.md]
                  assertions: ["file_exists:two.md"]
                """));

        assertThat(task.readFirst()).containsExactly("one.md");
        assertThat(task.outputFiles()).containsExactly("two.md", "three.md");
        assertThat(task.assertions()).containsExactly("file_exists:two.md");
    }

    @Test
    void nonExistentFileFails() {
        assertThatThrownBy(() -> loader.load(tempDir.resolve("absent.yaml")))
                .isInstanceOf(TaskLoadException.class)
                .satisfies(e -> assertThat(((TaskLoadException) e).category())
                        .isEqualTo("io"));
    }

    @Test
    void nonMappingRootFails() {
        assertThatThrownBy(() -> loader.load(writeTask("hello")))
                .isInstanceOf(TaskLoadException.class)
                .satisfies(e -> assertThat(((TaskLoadException) e).category())
                        .isEqualTo("schema"))
                .hasMessageContaining("mapping");
    }

    @Test
    void malformedYamlFails() {
        assertThatThrownBy(() -> loader.load(writeTask("a: [b")))
                .isInstanceOf(TaskLoadException.class)
                .satisfies(e -> assertThat(((TaskLoadException) e).category())
                        .isEqualTo("yaml"));
    }

    @Test
    void directoryInsteadOfFileFails() {
        assertThatThrownBy(() -> loader.load(tempDir))
                .isInstanceOf(TaskLoadException.class)
                .satisfies(e -> assertThat(((TaskLoadException) e).category())
                        .isEqualTo("io"));
    }

    @Test
    void emptyFileFails() {
        assertThatThrownBy(() -> loader.load(writeTask("")))
                .isInstanceOf(TaskLoadException.class)
                .satisfies(e -> assertThat(((TaskLoadException) e).category())
                        .isEqualTo("schema"));
    }

    private Path writeTask(String yaml) {
        Path file = tempDir.resolve("task.yaml");
        try {
            Files.writeString(file, yaml);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write test task", e);
        }
        return file;
    }
}
