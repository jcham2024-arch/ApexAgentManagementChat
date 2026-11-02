package com.apex.engine.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    private static final String VALID_TASK = """
            task_id: cli-task
            description: d
            instructions: i
            max_steps: 2
            tools: [read_file]
            """;

    @TempDir
    Path tempDir;

    private ByteArrayOutputStream outBytes;
    private ByteArrayOutputStream errBytes;
    private PrintStream out;
    private PrintStream err;

    @BeforeEach
    void setUp() {
        outBytes = new ByteArrayOutputStream();
        errBytes = new ByteArrayOutputStream();
        out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
    }

    @Test
    void noArgsReturnsUsageError() {
        int code = Main.run(new String[0], out, err);

        assertThat(code).isEqualTo(2);
        assertThat(stderr()).contains("usage");
    }

    @Test
    void extraArgsReturnsUsageError() {
        int code = Main.run(new String[] {"a", "b"}, out, err);

        assertThat(code).isEqualTo(2);
        assertThat(stderr()).contains("usage");
    }

    @Test
    void missingFileReturns1() {
        int code = Main.run(new String[] {"does-not-exist.yaml"}, out, err);

        assertThat(code).isEqualTo(1);
        assertThat(stderr()).contains("load failed [io]");
    }

    @Test
    void validTaskReturns0AndPrintsJson() {
        Path taskFile = write(VALID_TASK);

        int code = Main.run(new String[] {taskFile.toString()}, out, err);

        assertThat(code).isZero();
        assertThat(stdout()).contains("\"status\":\"STUB\"");
        assertThat(stdout().lines()).hasSize(1);
        assertThat(stderr()).isEmpty();
    }

    @Test
    void invalidTaskReturns1() {
        Path taskFile = write("""
                description: d
                instructions: i
                max_steps: 1
                tools: [read_file]
                """);

        int code = Main.run(new String[] {taskFile.toString()}, out, err);

        assertThat(code).isEqualTo(1);
        assertThat(stderr()).contains("task_id");
        assertThat(stdout()).isEmpty();
    }

    private Path write(String yaml) {
        Path file = tempDir.resolve("task.yaml");
        try {
            Files.writeString(file, yaml);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write test task", e);
        }
        return file;
    }

    private String stdout() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }
}
