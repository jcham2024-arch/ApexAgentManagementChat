package com.apex.engine.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The S2 milestone: task file in, real kernel run, JSON result out.
 * Only the model provider is mocked; SQLite, tools, the path jail,
 * RealTaskRunner and Main.run are the real implementations.
 */
class E2ETest {

    private static final String STEP_1 =
            "{\"action\":\"read_file\",\"arguments\":{\"path\":\"ARCHITECTURE.md\"}}";
    private static final String STEP_2 =
            "{\"action\":\"write_file\",\"arguments\":{\"path\":\"REPORT.md\","
            + "\"content\":\"Apex is a multi-agent platform.\"}}";
    private static final String STEP_3 = "{\"done\":true,\"summary\":\"wrote report\"}";

    @TempDir
    Path workspace;

    HttpServer server;
    AtomicInteger steps;
    ByteArrayOutputStream outBytes;
    ByteArrayOutputStream errBytes;
    PrintStream out;
    PrintStream err;

    @BeforeEach
    void startMockProvider() throws IOException {
        steps = new AtomicInteger();
        outBytes = new ByteArrayOutputStream();
        errBytes = new ByteArrayOutputStream();
        out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String response = scriptedResponse(steps.incrementAndGet());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();

        Files.writeString(workspace.resolve("ARCHITECTURE.md"),
                "Apex is a multi-agent platform.");
    }

    @AfterEach
    void stopMockProvider() {
        if (server != null) {
            server.stop(0);
        }
    }

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

        int code = Main.run(new String[] {"--real", "--workspace",
                workspace.toString(), taskFile.toString()}, env(), out, err);

        assertThat(code).isZero();
        assertThat(stdout()).contains("\"status\":\"COMPLETED\"");
        assertThat(workspace.resolve("REPORT.md")).exists();
        assertThat(Files.readString(workspace.resolve("REPORT.md")))
                .contains("Apex");
        assertThat(workspace.resolve(".apex/steps.db")).exists();
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

        int code = Main.run(new String[] {"--real", "--workspace",
                workspace.toString(), taskFile.toString()}, env(), out, err);

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

        int code = Main.run(new String[] {"--real", "--workspace",
                workspace.toString(), taskFile.toString()}, env(), out, err);

        assertThat(code).isEqualTo(1);
        assertThat(stderr()).contains("real mode requires model_route");
    }

    private static String scriptedResponse(int step) {
        return envelope(switch (step) {
            case 1 -> STEP_1;
            case 2 -> STEP_2;
            default -> STEP_3;
        });
    }

    /**
     * The mock must speak the OpenAI contract: the decision JSON is the
     * assistant message content, not the whole body. The phase file's
     * sketch returned the decision directly, which the real provider
     * cannot parse.
     */
    private static String envelope(String decisionJson) {
        String escaped = decisionJson
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"" + escaped + "\"}}]}";
    }

    private Map<String, String> env() {
        return Map.of(
                "APEX_LLM_BASE_URL",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "APEX_LLM_API_KEY", "test",
                "APEX_LLM_DEFAULT_MODEL", "mock-model",
                "APEX_LLM_TIMEOUT_SEC", "10");
    }

    private Path writeTask(String yaml) throws IOException {
        Path file = workspace.resolve("task.yaml");
        Files.writeString(file, yaml);
        return file;
    }

    private String stdout() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }
}
