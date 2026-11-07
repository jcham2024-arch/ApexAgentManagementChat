package com.apex.platform.app.real;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The S2 milestone and the S3.2 tenant proof: task file in, real kernel
 * run, JSON result out. Only the model provider is mocked; SQLite,
 * tools, the path jail, RealTaskRunner and RealMain.run are real.
 */
class E2ETest extends E2ETestSupport {

    @Test
    void kernelCompletesATaskFileEndToEnd() throws Exception {
        Path taskFile = writeTask("""
                task_id: e2e
                description: Read and summarize
                max_steps: 10
                model_route: "mock-model"
                tools: [read_file, write_file]
                instructions: Read ARCHITECTURE.md, write a summary to REPORT.md.
                outputs:
                  files: [REPORT.md]
                  assertions:
                    - "file_exists:REPORT.md"
                    - "file_contains:REPORT.md:Apex"
                """);

        int code = runMain(taskFile);

        assertThat(code).isZero();
        assertThat(stdout()).contains("\"status\":\"COMPLETED\"");
        assertThat(workspace.resolve("REPORT.md")).exists();
        assertThat(Files.readString(workspace.resolve("REPORT.md")))
                .contains("Apex");
        assertThat(workspace.resolve(".apex/steps.db")).exists();
        assertThat(recordedTenant()).isEqualTo("cli-default");
        assertThat(stderr()).isEmpty();
    }

    @Test
    void failedAssertionReturnsExitCodeOne() throws Exception {
        Path taskFile = writeTask("""
                task_id: e2e-failing
                description: Read and summarize
                max_steps: 10
                model_route: "mock-model"
                tools: [read_file, write_file]
                instructions: Read ARCHITECTURE.md, write a summary to REPORT.md.
                outputs:
                  assertions:
                    - "file_contains:REPORT.md:NOT_PRESENT"
                """);

        int code = runMain(taskFile);

        assertThat(code).isEqualTo(1);
        assertThat(stdout()).contains("\"status\":\"ASSERTION_FAILED\"");
    }

    @Test
    void realModeWithoutModelRouteFailsFast() throws Exception {
        Path taskFile = writeTask("""
                task_id: no-route
                description: d
                instructions: i
                max_steps: 2
                tools: [read_file]
                """);

        int code = runMain(taskFile);

        assertThat(code).isEqualTo(1);
        assertThat(stderr()).contains("real mode requires model_route");
    }
}
