package com.apex.engine.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Field-level schema failures. Kept apart from TaskLoaderTest so both
 * classes stay under the 200-line limit, as S2.6 5.1 allows.
 */
class TaskLoaderValidationTest {

    @TempDir
    Path tempDir;

    TaskLoader loader;

    @BeforeEach
    void setUp() {
        loader = new TaskLoader();
    }

    @Test
    void missingTaskIdFails() {
        assertSchemaFailure("""
                description: d
                instructions: i
                max_steps: 1
                tools: [read_file]
                """, "task_id");
    }

    @Test
    void missingDescriptionFails() {
        assertSchemaFailure("""
                task_id: t
                instructions: i
                max_steps: 1
                tools: [read_file]
                """, "description");
    }

    @Test
    void missingInstructionsFails() {
        assertSchemaFailure("""
                task_id: t
                description: d
                max_steps: 1
                tools: [read_file]
                """, "instructions");
    }

    @Test
    void missingMaxStepsFails() {
        assertSchemaFailure("""
                task_id: t
                description: d
                instructions: i
                tools: [read_file]
                """, "max_steps");
    }

    @Test
    void missingToolsFails() {
        assertSchemaFailure("""
                task_id: t
                description: d
                instructions: i
                max_steps: 1
                """, "tools");
    }

    @Test
    void emptyToolsListFails() {
        assertSchemaFailure("""
                task_id: t
                description: d
                instructions: i
                max_steps: 1
                tools: []
                """, "tools");
    }

    @Test
    void zeroMaxStepsFails() {
        assertSchemaFailure("""
                task_id: t
                description: d
                instructions: i
                max_steps: 0
                tools: [read_file]
                """, "max_steps");
    }

    @Test
    void blankTaskIdFails() {
        assertSchemaFailure("""
                task_id: "  "
                description: d
                instructions: i
                max_steps: 1
                tools: [read_file]
                """, "task_id");
    }

    @Test
    void nonStringToolElementFails() {
        assertSchemaFailure("""
                task_id: t
                description: d
                instructions: i
                max_steps: 1
                tools: [1, 2]
                """, "tools");
    }

    private void assertSchemaFailure(String yaml, String field) {
        assertThatThrownBy(() -> loader.load(writeTask(yaml)))
                .isInstanceOf(TaskLoadException.class)
                .satisfies(e -> assertThat(((TaskLoadException) e).category())
                        .isEqualTo("schema"))
                .hasMessageContaining(field);
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
